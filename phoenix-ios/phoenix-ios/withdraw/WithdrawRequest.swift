import Foundation
import PhoenixShared
import CryptoKit
import DnaCommunicator

fileprivate let filename = "WithdrawRequest"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WithdrawRequest {
	let piccData: Data
	let cmac: Data
	let method: WithdrawRequestMethod
	let amount: Lightning_kmpMilliSatoshi
	let databaseHash: String
	
	init(piccData: Data, cmac: Data, method: WithdrawRequestMethod, amount: Lightning_kmpMilliSatoshi) {
		self.piccData     = piccData
		self.cmac         = cmac
		self.method       = method
		self.amount       = amount
		self.databaseHash = Self.calculateDatabaseHash(
			piccData: piccData, cmac: cmac, method: method, amount: amount
		)
	}
	
	/// We use a hash to mark the request as "processed" within the database.
	/// The hash encompasses all the relavent parts of the request.
	///
	private static func calculateDatabaseHash(
		piccData : Data,
		cmac     : Data,
		method   : WithdrawRequestMethod,
		amount   : Lightning_kmpMilliSatoshi
	) -> String {
		
		var hashMe = Data()
		hashMe.append(piccData.toHex(.lowerCase).data(using: .utf8)!)
		hashMe.append(cmac.toHex(.lowerCase).data(using: .utf8)!)
		hashMe.append(method.encode().data(using: .utf8)!)
		hashMe.append(String(amount.msat).data(using: .utf8)!)

		let digest = SHA256.hash(data: hashMe)
		return digest.toHex(.lowerCase)
	}
}

enum WithdrawRequestMethod {
	case bolt11Invoice(invoice: Lightning_kmpBolt11Invoice)
	case bolt12Invoice(invoice: Lightning_kmpBolt12Invoice)
	
	func encode() -> String {
		switch self {
		case .bolt11Invoice(let invoice):
			return invoice.write()
			
		case .bolt12Invoice(let invoice):
			return invoice.write()
		}
	}
	
	var description: String? {
		switch self {
		case .bolt11Invoice(let invoice):
			return invoice.description_
			
		case .bolt12Invoice(let invoice):
			return invoice.description_
		}
	}
}

enum WithdrawRequestStatus {
	case continueAndSendPayment(
		card   : BoltCardInfo,
		method : WithdrawRequestMethod,
		amount : Lightning_kmpMilliSatoshi
	)
	case abortHandledElsewhere(card: BoltCardInfo)
}

enum WithdrawRequestError: Error, CustomStringConvertible {
	case unknownCard
	case replayDetected(card: BoltCardInfo)
	case frozenCard(card: BoltCardInfo)
	case dailyLimitExceeded(card: BoltCardInfo, amount: CurrencyAmount)
	case monthlyLimitExceeded(card: BoltCardInfo, amount: CurrencyAmount)
	case badInvoice(card: BoltCardInfo, details: String)
	case alreadyPaidInvoice(card: BoltCardInfo)
	case paymentPending(card: BoltCardInfo)
	case internalError(card: BoltCardInfo?, details: String)
	
	var description: String {
		return switch self {
			case .unknownCard                   : "unknown card"
			case .replayDetected                : "replay detected"
			case .frozenCard                    : "frozen card"
			case .dailyLimitExceeded            : "daily limit exceeded"
			case .monthlyLimitExceeded          : "monthly limit exceeded"
			case .badInvoice(_, let details)    : "bad invoice: \(details)"
			case .alreadyPaidInvoice            : "already paid invoice"
			case .paymentPending                : "payment pending"
			case .internalError(_, let details) : "internal error: \(details)"
		}
	}
	
	var cardResponseMessage: String {
		return switch self {
			case .unknownCard                : "unknown card"
			case .replayDetected             : "replay detected"
			case .frozenCard                 : "frozen card"
			case .dailyLimitExceeded         : "limit exceeded" // don't expose daily/monthly type
			case .monthlyLimitExceeded       : "limit exceeded" // don't expose daily/monthly type
			case .badInvoice(_, let details) : "bad invoice: \(details)"
			case .alreadyPaidInvoice         : "already paid invoice"
			case .paymentPending             : "payment pending"
			case .internalError(_, _)        : "internal error" // don't expose internal error details
		}
	}
	
	var cardResponseCode: CardResponse.ErrorCode {
		return switch self {
			case .unknownCard                : CardResponse.ErrorCode.unknownCard
			case .replayDetected(_)          : CardResponse.ErrorCode.replayDetected
			case .frozenCard(_)              : CardResponse.ErrorCode.frozenCard
			case .dailyLimitExceeded(_, _)   : CardResponse.ErrorCode.limitExceeded
			case .monthlyLimitExceeded(_, _) : CardResponse.ErrorCode.limitExceeded
			case .badInvoice(_, _)           : CardResponse.ErrorCode.badInvoice
			case .alreadyPaidInvoice(_)      : CardResponse.ErrorCode.alreadyPaidInvoice
			case .paymentPending(_)          : CardResponse.ErrorCode.paymentPending
			case .internalError(_, _)        : CardResponse.ErrorCode.internalError
		}
	}
}

extension PhoenixBusiness {
	
	@MainActor
	func checkWithdrawRequest(
		_ request: WithdrawRequest
	) async -> Result<WithdrawRequestStatus, WithdrawRequestError> {
		
		log.trace(#function)
		
		// Step 1 of 7:
		// Decrypt the piccData & verify the cmac values.
		//
		// Note that the user may have multiple cards,
		// and we don't know which card is sending the request.
		// So we simply make an attempt with each linked card.
		
		let cardsDb: SqliteCardsDb
		do {
			cardsDb = try await self.databaseManager.cardsDb()
		} catch {
			return .failure(.internalError(card: nil, details: "card database unavailable"))
		}
		
		var cards: [BoltCardInfo] = cardsDb.cardsListValue
		if cards.isEmpty {
			// The cardsList instance may not be ready yet.
			// So we work around this by directly querying the database.
			//
			do {
				cards = try await cardsDb.listCards()
			} catch {
				log.error("appDb.listCards(): error: \(error)")
			}
		}
		
		log.debug("cards.count = \(cards.count)")
		
		var matchingCard: BoltCardInfo? = nil
		var piccDataInfo: Ntag424.PiccDataInfo? = nil
		
		for card in cards {
			
			if card.isForeign {
				// We only manage foreign cards.
				// They cannot be used for payments from this wallet.
				continue
			}
			
			let keySet = Ntag424.KeySet(
				piccDataKey : card.keys.piccDataKey_data,
				cmacKey     : card.keys.cmacKey_data
			)
			let result = Ntag424.extractPiccDataInfo(
				piccData : request.piccData,
				cmac     : request.cmac,
				keySet   : keySet
			)
			
			switch result {
			case .failure(let err):
				log.debug("card[\(card.id)]: err: \(err)")
				
			case .success(let result):
				log.debug("card[\(card.id)]: success")
				
				matchingCard = card
				piccDataInfo = result
				break
			}
		}
		
		guard let matchingCard, let piccDataInfo else {
			return .failure(.unknownCard)
		}
		
		// Step 2 of 7:
		// Check to make sure the counter has been incremented.
		
		guard piccDataInfo.counter > matchingCard.lastKnownCounter else {
			return .failure(.replayDetected(card: matchingCard))
		}
		
		// From this point forward:
		//
		// The last step we should perform, before returning the result,
		// is updating the CardInfo within the database.
		// We want to ensure we update the `lastKnownCounter` value
		// to protect against replay attacks.
		
		let asyncDeferred = { @MainActor (result: Result<WithdrawRequestStatus, WithdrawRequestError>) async
			-> Result<WithdrawRequestStatus, WithdrawRequestError> in
		
			var shouldUpdateCard = true
			if case .success(let status) = result {
				if case .abortHandledElsewhere = status {
					shouldUpdateCard = false
				}
			}
			
			if shouldUpdateCard {
				let updatedCard = matchingCard.withUpdatedLastKnownCounter(piccDataInfo.counter)
				do {
					try await cardsDb.saveCard(card: updatedCard)
				} catch {
					log.error("cardsManager.saveCard(): error: \(error)")
				}
			}
			
			return result
		}
		
		// Step 3 of 7:
		// Check to make sure the card isn't frozen.
		
		guard matchingCard.isActive else {
			log.debug("card[\(matchingCard.id)]: isFrozen")
			return await asyncDeferred(.failure(.frozenCard(card: matchingCard)))
		}
		
		// Step 4 of 7:
		// Validate the invoice.
		//
		// We know the invoice is technically valid (not malformed),
		// but there are additional checks we need to perform such as:
		//
		// - chain mismatch (e.g. invoice is for mainnet but we're on testnet)
		// - invoice is expired
		// - already paid invoice
		// - invoice has payment pending
		//
		// The SendManager has standardized code to perform these checks.
		
		do {
			let badRequestReason: SendManager.BadRequestReason?
			
			switch request.method {
			case .bolt11Invoice(let invoice):
				badRequestReason = try await self.sendManager.checkForBadBolt11Invoice(invoice: invoice)
				
			case .bolt12Invoice(let invoice):
				badRequestReason = try await self.sendManager.checkForBadBolt12Invoice(invoice: invoice)
			}
			
			if let badRequestReason {
				log.debug("SendManager.BadRequestReason: \(badRequestReason)")
				
				switch onEnum(of: badRequestReason) {
				case .alreadyPaidInvoice(_):
					return await asyncDeferred(.failure(.alreadyPaidInvoice(card: matchingCard)))
					
				case .paymentPending(_):
					return await asyncDeferred(.failure(.paymentPending(card: matchingCard)))
					
				case .expired(_):
					return await asyncDeferred(.failure(.badInvoice(card: matchingCard, details: "expired")))
					
				case .chainMismatch(_):
					return await asyncDeferred(.failure(.badInvoice(card: matchingCard, details: "chain mismatch")))
					
				default:
					return await asyncDeferred(.failure(.badInvoice(card: matchingCard, details: "parse error")))
				}
			}
			
		} catch {
			log.error("SendManager.checkForBadBolt1XInvoice: threw error: \(error)")
			return await asyncDeferred(.failure(.internalError(card: matchingCard, details: "validation error")))
		}
		
		// Step 5 of 7:
		// Check the amount against any set daily/monthly spending limits.
		
		let checkSpendingLimit = {(
			cardAmounts : SqliteCardsDb.CardAmounts,
			limit       : CurrencyAmount,
			isDaily     : Bool
		) -> WithdrawRequestError? in
			
			let invoiceMsat: Int64 = request.amount.msat
			
			switch limit.currency {
			case .bitcoin(let bitcoinUnit):
				let limitMsat: Int64 = Utils.toMsat(from: limit.amount, bitcoinUnit: bitcoinUnit)
				
				let prvSpendMsat: Int64 = isDaily
					? cardAmounts.dailyBitcoinAmount().msat
					: cardAmounts.monthlyBitcoinAmount().msat
				
				let newSpendMsat: Int64 = prvSpendMsat + invoiceMsat
				
				log.debug(
					"""
					\(isDaily ? "dailySpendingLimit" : "monthlySpendingLimit"): \
					prvSpendMsat(\(prvSpendMsat)) + invoiceMsat(\(invoiceMsat)) = \
					newSpendMsat(\(newSpendMsat)) ?>? limitMsat(\(limitMsat))
					""")
				
				if newSpendMsat > limitMsat {
					let targetAmt = Utils.convertBitcoin(msat: invoiceMsat, to: bitcoinUnit)
					let currencyAmt = CurrencyAmount(currency: limit.currency, amount: targetAmt)
					
					return isDaily
						? .dailyLimitExceeded(card: matchingCard, amount: currencyAmt)
						: .monthlyLimitExceeded(card: matchingCard, amount: currencyAmt)
				}
				
			case .fiat(let fiatCurrency):
				let limitFiat: Double = limit.amount
				
				let exchangeRates = self.phoenixGlobal.currencyManager.ratesFlowValue
				guard let exchangeRate = Utils.exchangeRate(for: fiatCurrency, fromRates: exchangeRates) else {
					return .internalError(card: matchingCard, details: "missing exchange rate")
				}
				let invoiceFiat: Double = Utils.convertToFiat(msat: invoiceMsat, exchangeRate: exchangeRate)
				
				let prvSpendFiat: Double = isDaily
					? cardAmounts.dailyFiatAmount(target: fiatCurrency, exchangeRates: exchangeRates)
					: cardAmounts.monthlyFiatAmount(target: fiatCurrency, exchangeRates: exchangeRates)
				
				let newSpendFiat: Double = prvSpendFiat + invoiceFiat
				
				log.debug(
					"""
					\(isDaily ? "dailySpendingLimit" : "monthlySpendingLimit"): \
					prvSpendFiat(\(prvSpendFiat)) + invoiceFiatt(\(invoiceFiat)) = \
					newSpendFiat(\(newSpendFiat)) ?>? limitFiat(\(limitFiat))
					""")
				
				if newSpendFiat > limitFiat {
					let targetAmt = CurrencyAmount(currency: limit.currency, amount: invoiceFiat)
					
					return isDaily
						? .dailyLimitExceeded(card: matchingCard, amount: targetAmt)
						: .monthlyLimitExceeded(card: matchingCard, amount: targetAmt)
				}
			}
			
			return nil
		}
		
		if matchingCard.dailyLimit != nil || matchingCard.monthlyLimit != nil {
		
			do {
				let cardPayments: SqliteCardsDb.CardPayments =
					try await cardsDb.fetchCardPayments(cardId: matchingCard.id)
				
				let cardAmounts = cardsDb.getCardAmounts(payments : cardPayments)
				
				if let dailyLimit = matchingCard.dailyLimit?.toCurrencyAmount() {
					if let error = checkSpendingLimit(cardAmounts, dailyLimit, true) {
						return await asyncDeferred(.failure(error))
					}
				}
				if let monthlyLimit = matchingCard.monthlyLimit?.toCurrencyAmount() {
					if let error = checkSpendingLimit(cardAmounts, monthlyLimit, false) {
						return await asyncDeferred(.failure(error))
					}
				}
				
			} catch {
				return await asyncDeferred(.failure(
					.internalError(card: matchingCard, details: "checking spending limits")
				))
			}
		}
		
		// Step 6 of 7:
		// Wait until our peer is connected & all channels are ready.
		//
		// Note that there are safety mechanisms in place to ensure that
		// only one process (mainPhoenixApp vs notifySrvExt) is able to
		// connect to the peer at a time.
		// That's why this step must preceed the following step.
		
		let target = AppConnectionsDaemon.ControlTarget.companion.Peer
		for try await connections in self.connectionsManager.connectionsSequence() {
			
			log.debug("connections = \(connections)")
			if connections.targetsEstablished(target) {
				log.debug("Connected to peer")
				break
			}
		}
		
		for try await channels in self.peerManager.channelsArraySequence() {
			let allChannelsReady = channels.allSatisfy { $0.isTerminated || $0.isUsable }
			if allChannelsReady {
				log.debug("All channels ready")
				break
			} else {
				log.debug("One or more channels not ready...")
			}
		}
		
		// Step 7 of 7:
		// Atomically mark request as handled.
		//
		// At this point we've decided that it's safe to pay the invoice.
		// The only question is WHO is going to pay it:
		// - mainPhoenixApp (foreground process / main app with user interface)
		// - notifySrvExt   (background process that could be running in response to a notification)
		//
		// So to be sure we don't accidentally pay an invoice TWICE,
		// we have an atomic database method that will fail if the other
		// process has already marked it as handled.
		
		let handledByUs = await self.phoenixGlobal.appDb.tryMarkHandled(request)
		
		if handledByUs {
			return await asyncDeferred(.success(.continueAndSendPayment(
				card: matchingCard, method: request.method, amount: request.amount
			)))
		} else {
			// The payment is being handled else.
			// Or has already been handled elsewhere.
			// Probably by the notifySrvExt.
			//
			// So we need to abort processing (do NOT pay invoice).
			//
			return await asyncDeferred(.success(.abortHandledElsewhere(card: matchingCard)))
		}
	}
}

extension SqliteAppDb {
	
	enum ProcessId: String, Codable {
		case phoenixApp   = "phoenixApp"
		case notifySrvExt = "notifySrvExt"
	}

	struct WithdrawRequestHandler: Codable {
		let hash: String
		let process: ProcessId
		let date: Date
	}
	
	@MainActor
	func tryMarkHandled(_ request: WithdrawRequest) async -> Bool {
		
		let process: ProcessId
		switch AppIdentifier.current {
			case .foreground: process = .phoenixApp
			case .background: process = .notifySrvExt
		}
		
		let key = "WithdrawRequestHandlers"
		do {
			while true {
				let existing: KotlinPair<KotlinByteArray, KotlinLong>? = try await self.getValue(key: key)
				
				var handlers: [WithdrawRequestHandler] = []
				if let existing, let existingData = existing.first?.toSwiftData() {
					
					handlers = try JSONDecoder().decode([WithdrawRequestHandler].self, from: existingData)
				}
				
				log.debug("tryMarkHandled(): existing handlers.count = \(handlers.count)")
				
				let isHandledAlready = handlers.contains(where: { (item: WithdrawRequestHandler) in
					item.hash == request.databaseHash
				})
				
				if isHandledAlready {
					log.debug("tryMarkHandled(): isHandledAlready")
					return false
				}
				
				if !handlers.isEmpty {
					// Cleanup: remove any handlers older than 7 days
					
					let oldDate = Date.now.addingTimeInterval(60 * 60 * 24 * -7)
					handlers.removeAll(where: { item in
						item.date < oldDate
					})
					
					log.debug("tryMarkHandled(): post-clean: handlers.count = \(handlers.count)")
				}
				
				handlers.append(WithdrawRequestHandler(
					hash    : request.databaseHash,
					process : process,
					date    : Date.now
				))
				
				log.debug("tryMarkHandled(): new handlers.count = \(handlers.count)")
				
				let updatedData = try JSONEncoder().encode(handlers)
				let lastUpdated: KotlinLong? = existing?.second
				
				log.debug("tryMarkHandled(): lastUpdated = \(lastUpdated?.description ?? "<nil>")")
				
				let result = try await setValueIfUnchanged(
					value       : updatedData.toKotlinByteArray(),
					key         : key,
					lastUpdated : lastUpdated
				)
				
				log.debug("tryMarkHandled(): result = \(result?.description ?? "<nil>")")
				
				if result != nil {
					return true
				} else {
					// The call to setValueIfUnchanged failed.
					// But that could happen for a number of reasons:
					// - background app marked this request as handled
					// - background app marked a different request as handled
					// - foreground app marked a different request as handled
					//
					// So we need to start the process over again.
				}
				
			} // </while true>
			
		} catch {
			log.error("tryMarkHandled(): error: \(error)")
			return false
		}
	}
}
