import Foundation
import PhoenixShared
import CryptoKit

fileprivate let filename = "WithdrawRequest"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WithdrawRequest {
	let nodeId: String
	let piccData: Data
	let cmac: Data
	let invoice: String
	let timestamp: Date
	let withdrawHash: String
	
	init(nodeId: String, piccData: Data, cmac: Data, invoice: String, timestamp: Date) {
		self.nodeId = nodeId
		self.piccData = piccData
		self.cmac = cmac
		self.invoice = invoice
		self.timestamp = timestamp
		self.withdrawHash = Self.calculateWithdrawHash(
			nodeId: nodeId, piccData: piccData, cmac: cmac, invoice: invoice
		)
	}
	
	private static func calculateWithdrawHash(
		nodeId   : String,
		piccData : Data,
		cmac     : Data,
		invoice  : String
	) -> String {
		
		var hashMe = Data()
		hashMe.append(nodeId.lowercased().data(using: .utf8)!)
		hashMe.append(piccData.toHex(options: .lowerCase).data(using: .utf8)!)
		hashMe.append(cmac.toHex(options: .lowerCase).data(using: .utf8)!)
		hashMe.append(invoice.data(using: .utf8)!)
		
		let digest = SHA256.hash(data: hashMe)
		return digest.toHex(options: .lowerCase)
	}
	
	func postResponse(errorReason: String?) async -> Bool {
		log.trace("postResponse(\(errorReason ?? "<nil>"))")
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnurlw/response")!
		
		var body: [String: String] = [
			"node_id"       : nodeId,
			"withdraw_hash" : withdrawHash,
		]
		if let errorReason {
			body["err_message"] = errorReason
		}
		
		let bodyData = try? JSONSerialization.data(
			withJSONObject: body,
			options: []
		)
		
		var request = URLRequest(url: url)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		do {
			log.debug("/v1/pub/lnurlw/response: sending...")
			let (data, response) = try await URLSession.shared.data(for: request)
			
			var statusCode = 418
			var success = false
			if let httpResponse = response as? HTTPURLResponse {
				statusCode = httpResponse.statusCode
				if statusCode >= 200 && statusCode < 300 {
					success = true
				}
			}
			
			if success {
				log.debug("/v1/pub/lnurlw/response: success")
			} else {
				log.debug("/v1/pub/lnurlw/response: statusCode: \(statusCode)")
				if let dataString = String(data: data, encoding: .utf8) {
					log.debug("/v1/pub/lnurlw/response: response:\n\(dataString)")
				}
			}
			
			return success
		} catch {
			log.debug("/v1/pub/lnurlw/response: error: \(String(describing: error))")
			return false
		}
	}
}

enum WithdrawRequestStatus {
	case continueAndSendPayment(
		card    : BoltCardInfo,
		invoice : Lightning_kmpBolt11Invoice,
		amount  : Lightning_kmpMilliSatoshi
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
	case internalError(card: BoltCardInfo, details: String)
	
	var description: String {
		switch self {
			case .unknownCard          : return "unknown card"
			case .replayDetected       : return "replay detected"
			case .frozenCard           : return "frozen card"
			case .dailyLimitExceeded   : return "daily limit exceeded"
			case .monthlyLimitExceeded : return "monthly limit exceeded"
			case .badInvoice           : return "bad invoice"
			case .alreadyPaidInvoice   : return "already paid invoice"
			case .paymentPending       : return "payment pending"
			case .internalError        : return "internal error"
		}
	}
}

extension PhoenixBusiness {
	
	@MainActor
	func checkWithdrawRequest(
		_ request: WithdrawRequest
	) async -> Result<WithdrawRequestStatus, WithdrawRequestError> {
		
		log.trace("checkWithdrawRequest()")
		
		// Step 1 of 9:
		// Decrypt the piccData & verify the cmac values.
		//
		// Note that the user may have multiple cards,
		// and we don't know which card is sending the request.
		// So we simply make an attempt with each linked card.
		
		var cards: [BoltCardInfo] = self.cardsManager.cardsListValue
		if cards.isEmpty {
			// The CardManager instance is created lazily.
			// And if we triggered creation just now,
			// then the `cardsList` hasn't had time to update yet.
			// So we work around this by directly querying the database.
			//
			do {
				cards = try await self.appDb.listCards()
			} catch {
				log.error("appDb.listCards(): error: \(error)")
			}
		}
		
		log.debug("cards.count = \(cards.count)")
		
		var matchingCard: BoltCardInfo? = nil
		var piccDataInfo: Ntag424.PiccDataInfo? = nil
		
		for card in cards {
			
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
		
		// Step 2 of 9:
		// Check to make sure the counter has been incremented.
		
		guard piccDataInfo.counter > matchingCard.lastKnownCounter else {
			return .failure(.replayDetected(card: matchingCard))
		}
		
		
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
					try await self.cardsManager.saveCard(card: updatedCard)
				} catch {
					log.error("cardsManager.saveCard(): error: \(error)")
				}
			}
			
			return result
		}
		
		// Step 3 of 9:
		// Check to make sure the card isn't frozen.
		
		guard matchingCard.isActive else {
			log.debug("card[\(matchingCard.id)]: isFrozen")
			return await asyncDeferred(.failure(.frozenCard(card: matchingCard)))
		}
		
		// Step 4 of 9:
		// Check to make sure the given invoice is a valid Bolt11 invoice.
		
		guard let invoice = Parser.shared.readBolt11Invoice(input: request.invoice) else {
			log.debug("request.invoice is not Bolt11Invoice")
			return await asyncDeferred(.failure(.badInvoice(card: matchingCard, details: "not bolt 11 invoice")))
		}
		
		guard let invoiceAmount: Lightning_kmpMilliSatoshi = invoice.amount else {
			log.debug("request.invoice.amount is nil")
			return await asyncDeferred(.failure(.badInvoice(card: matchingCard, details: "amountless invoice")))
		}
		
		// Step 5 of 9:
		// Validate the invoice.
		//
		// We know the invoice is a proper Bolt 11 invoice.
		// But the SendManager performs additional checks such as:
		// - chain mismatch
		// - invoice is expired
		// - already paid invoice
		// - invoice has payment pending
		//
		// So we use the SendManager to perform those checks.
		//
		// Note that we already know the input is Bolt11 invoice,
		// so we know which route it will take thru the parser.
		
		do {
			let result: SendManager.ParseResult =
				try await self.sendManager.parse(
					request: request.invoice,
					progress: { _ in /* ignore */ }
				)
			
			switch onEnum(of: result) {
			case .badRequest(let badRequest):
				log.debug("SendManager.ParseResult = BadRequest: \(badRequest)")
				
				switch onEnum(of: badRequest.reason) {
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
				
			case .success(_):
				log.debug("SendManager.ParseResult = Success")
			}
			
		} catch {
			log.error("SendManager.parse(): threw error: \(error)")
			return await asyncDeferred(.failure(.internalError(card: matchingCard, details: "parse error")))
		}
		
		// Step 6 of 9:
		// Check the amount against any set daily/monthly spending limits.
		
		let checkSpendingLimit = {
		(cardAmounts: CardsManager.CardAmounts, limit: CurrencyAmount, isDaily: Bool) -> WithdrawRequestError? in
			
			switch limit.currency {
			case .bitcoin(let bitcoinUnit):
				let limitMsat: Int64 = Utils.toMsat(from: limit.amount, bitcoinUnit: bitcoinUnit)
				
				let prvSpendMsat: Int64 = isDaily
					? cardAmounts.dailyBitcoinAmount().msat
					: cardAmounts.monthlyBitcoinAmount().msat
				
				let newSpendMsat: Int64 = prvSpendMsat + invoiceAmount.msat
				
				log.debug(
					"""
					\(isDaily ? "dailySpendingLimit" : "monthlySpendingLimit"): \
					prvSpendMsat(\(prvSpendMsat)) + invoiceMsat(\(invoiceAmount.msat)) = \
					newSpendMsat(\(newSpendMsat)) ?>? limitMsat(\(limitMsat))
					""")
				
				if newSpendMsat > limitMsat {
					let targetAmt = Utils.convertBitcoin(msat: invoiceAmount.msat, to: bitcoinUnit)
					let currencyAmt = CurrencyAmount(currency: limit.currency, amount: targetAmt)
					
					return isDaily
						? .dailyLimitExceeded(card: matchingCard, amount: currencyAmt)
						: .monthlyLimitExceeded(card: matchingCard, amount: currencyAmt)
				}
				
			case .fiat(let fiatCurrency):
				let limitFiat: Double = limit.amount
				
				let exchangeRates = self.currencyManager.ratesFlowValue
				guard let exchangeRate = Utils.exchangeRate(for: fiatCurrency, fromRates: exchangeRates) else {
					return .internalError(card: matchingCard, details: "missing exchange rate")
				}
				let invoiceFiat: Double = Utils.convertToFiat(
					msat: invoiceAmount.msat,
					exchangeRate: exchangeRate
				)
				
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
				let cardPayments: CardsManager.CardPayments =
					try await self.cardsManager.fetchCardPayments(cardId: matchingCard.id)
				
				let cardAmounts = self.cardsManager.getCardAmounts(payments : cardPayments)
				
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
		
		// Step 7 of 9:
		// Wait until our peer is connected & all channels are ready.
		//
		// Note that there are safety mechanisms in place to ensure that
		// only one process (mainPhoenixApp vs notifySrvExt) is able to
		// connect to the peer at a time.
		// That's why this step must preceed the following step.
		
		let target = AppConnectionsDaemon.ControlTarget.companion.Peer
		for try await connections in self.connectionsManager.connectionsPublisher().values {
			
			log.debug("connections = \(connections)")
			if connections.targetsEstablished(target) {
				log.debug("Connected to peer")
				break
			}
		}
		
		for try await channels in self.peerManager.channelsPublisher().values {
			let allChannelsReady = channels.allSatisfy { $0.isTerminated || $0.isUsable || $0.isLegacyWait }
			if allChannelsReady {
				log.debug("All channels ready")
				break
			} else {
				log.debug("One or more channels not ready...")
			}
		}
		
		// Step 8 of 9:
		// Atomically mark request as handled.
		//
		// At this point we've decided that it's safe to pay the invoice.
		// The only question is WHO is going to pay it:
		// - mainPhoenixApp (us)
		// - notifySrvExt   (background process that could be running)
		//
		// So to be sure we don't accidentally pay an invoice TWICE,
		// we have an atomic database method that will fail if the other
		// process has already marked it as handled.
		
		let handledByUs = await self.appDb.tryMarkHandled(request, process: .phoenixApp)
		
		if handledByUs {
			return await asyncDeferred(.success(.continueAndSendPayment(
				card: matchingCard, invoice: invoice, amount: invoiceAmount
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

	struct LnurlWithdrawHandler: Codable {
		let withdrawHash: String
		let process: ProcessId
		let date: Date
	}
	
	@MainActor
	func tryMarkHandled(_ request: WithdrawRequest, process: ProcessId) async -> Bool {
		
		let key = "lnurlWithdrawHandlers"
		do {
			while true {
				let existing: KotlinPair<KotlinByteArray, KotlinLong>? = try await self.getValue(key: key)
				
				var handlers: [LnurlWithdrawHandler] = []
				if let existing, let existingData = existing.first?.toSwiftData() {
					
					handlers = try JSONDecoder().decode([LnurlWithdrawHandler].self, from: existingData)
				}
				
				log.debug("tryMarkHandled(): existing handlers.count = \(handlers.count)")
				
				let isHandledAlready = handlers.contains(where: { (item: LnurlWithdrawHandler) in
					item.withdrawHash == request.withdrawHash
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
				
				handlers.append(LnurlWithdrawHandler(
					withdrawHash : request.withdrawHash,
					process      : process,
					date         : Date.now
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
