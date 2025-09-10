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
		// - Amazon Web Services (AWS)
		
	#if DEBUG
		var push = PushNotification.parse(userInfo)
		
		// Still waiting for nodeId to be included in push notifications
		if case .fcm(let notification) = push {
			if notification.nodeIdHash == nil {
				let nodeId = "03a496f0414de4ed699d99a6e922da4e96e689a9312d2340bf85ff69688e6e4ef6"
				push = PushNotification.fcm(notification: FcmPushNotification(
					reason: notification.reason,
					nodeIdHash: hash160(nodeId: nodeId).successValue
				))
			}
		}
		
	#else
		let push = PushNotification.parse(userInfo)
	#endif
		
		if let push {
			switch push {
			case .fcm(let notification):
				processRemoteNotification_fcm(notification, completionHandler)
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

