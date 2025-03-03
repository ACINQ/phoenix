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
		log.trace("processRemoteNotification()")
		
		// This could be a push notification coming from either:
		// - Google's Firebase Cloud Messaging (FCM)
		// - Amazon Web Services (AWS)
		
		if PushNotification.isFCM(userInfo: userInfo) {
			processRemoteNotification_fcm(userInfo, completionHandler)
		} else {
			processRemoteNotification_aws(userInfo, completionHandler)
		}
	}
	
	private static func processRemoteNotification_fcm(
		_ userInfo: [AnyHashable : Any],
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace("processRemoteNotification_fcm()")
		
		// All FCM notifications are for incoming payments.
		//
		// If the app is in the foreground:
		// - we can ignore this notification
		//
		// If the app is in the background:
		// - this notification was delivered to the notifySrvExt, which is in charge of processing it
		
		invoke(completionHandler, .noData)
	}
	
	private static func processRemoteNotification_aws(
		_ userInfo: [AnyHashable : Any],
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace("processRemoteNotification_aws()")
		
		if let withdrawRequest = PushNotification.parseLnurlWithdraw(userInfo: userInfo) {
			Task {
				await processRemoteNotification_aws_withdraw(withdrawRequest, completionHandler)
			}
		} else {
			log.error("processRemoteNotification_aws: missing/invalid `acinq` section")
			invoke(completionHandler, .noData)
		}
	}
	
	@MainActor
	private static func processRemoteNotification_aws_withdraw(
		_ request: LnurlWithdrawNotification,
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) async {
		
		log.trace("processRequest_aws_withdraw()")
		
		let result = await Biz.business.checkWithdrawRequest(request.toWithdrawRequest())
		
		switch result {
		case .failure(let error):
			return reject(request, error, completionHandler)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere:
				return invoke(completionHandler, .newData)
			
			case .continueAndSendPayment(let card, _, _):
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
		log.trace("accept()")
		
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
