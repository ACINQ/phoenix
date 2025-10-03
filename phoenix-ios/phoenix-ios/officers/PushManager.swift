import SwiftUI
import PhoenixShared
import CryptoKit

fileprivate let filename = "PushManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class PushManager {
	
	public static func processRemoteNotification(
		_ userInfo: [AnyHashable : Any],
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace(#function)
		
		// This could be a push notification coming from either:
		// - Google's Firebase Cloud Messaging (FCM)
		// - Amazon Web Services (AWS) (only used for debugging)
		
		let push = PushNotification.parse(userInfo)
		
		if let push {
			switch push {
			case .fcm(let notification):
				processRemoteNotification_fcm(notification, completionHandler)
			case .lnurlWithdraw(let notification):
				Task {
					await processRemoteNotification_aws_withdraw(notification, completionHandler)
				}
			}
			
		} else {
			log.warning("\(#function): Failed to parse userInfo as PushNotification")
			invoke(completionHandler, .noData)
		}
	}
	
	private static func processRemoteNotification_fcm(
		_ notification: FcmPushNotification,
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace(#function)
		
		// If we receive a push notification that's not for the current wallet,
		// then we'll try to launch the associated `BusinessManager` in the background
		// to process an incoming payment.
		
		if let nodeIdHash = notification.nodeIdHash {
		
			let pushTargetIsCurrentWallet: Bool
			if let walletInfo = Biz.walletInfo, walletInfo.nodeIdHash == nodeIdHash {
				pushTargetIsCurrentWallet = true
			} else {
				pushTargetIsCurrentWallet = false
			}
			log.debug("pushTargetIsCurrentWallet = \(pushTargetIsCurrentWallet)")

			if !pushTargetIsCurrentWallet && notification.reason != .unknown {
				MBiz.launchBackgroundBiz(notification)
			}
			invoke(completionHandler, .newData)
			
		} else {
			log.warning("notification.nodeId or notification.chain is nil")
			invoke(completionHandler, .noData)
		}
	}
	
	@MainActor
	private static func processRemoteNotification_aws_withdraw(
		_ request: LnurlWithdrawNotification,
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) async {
		
		log.trace(#function)
		
		let result = await Biz.business.checkWithdrawRequest(request.toWithdrawRequest())
		
		switch result {
		case .failure(let error):
			log.error("\(#function): error: \(error.description)")
			return reject(request, error, completionHandler)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere:
				log.warning("\(#function): abort: handled elsewhere")
				return invoke(completionHandler, .newData)
			
			case .continueAndSendPayment(let card, _, _):
				log.debug("\(#function): continue: send payment")
				
				guard
					let peer = Biz.business.peerManager.peerStateValue(),
					let defaultTrampolineFees = peer.walletParams.trampolineFees.first
				else {
					return reject(
						request,
						.internalError(card: card, details: "peer is nil"),
						completionHandler
					)
				}
				
				do {
					try await Biz.business.sendManager.payBolt11Invoice(
						amountToSend   : request.invoiceAmount,
						trampolineFees : defaultTrampolineFees,
						invoice        : request.invoice,
						metadata       : WalletPaymentMetadata.withCard(card.id)
					)
				} catch {
					log.error("SendManager.payBolt11Invoice(): threw error: \(error)")
					return reject(
						request,
						.internalError(card: card, details: "payBolt11Invoice failed"),
						completionHandler
					)
				}
				
				return accept(request, completionHandler)
			} // </switch status>
		} // </switch result>
	}
	
	private static func reject(
		_ request : LnurlWithdrawNotification,
		_ error   : WithdrawRequestError,
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace("reject(\(error.description))")
		
		Task {
			let _ = await request.postResponse(errorReason: error.description)
			invoke(completionHandler, .newData)
		}
	}
	
	private static func accept(
		_ request: LnurlWithdrawNotification,
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace(#function)
		
		Task {
			let _ = await request.postResponse(errorReason: nil)
			invoke(completionHandler, .newData)
		}
	}
	
	private static func invoke(
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void,
		_ result: UIBackgroundFetchResult
	) {
		log.trace("invoke(completionHandler, \(result))")
		
		DispatchQueue.main.async {
			completionHandler(result)
		}
	}
}

