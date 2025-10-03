import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "KotlinPublishers+Lightning"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension Lightning_kmpPeer {
	
	func eventsFlowSequence() -> AnyAsyncSequence<Lightning_kmpPeerEvent> {
		
		return self.eventsFlow
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

extension Lightning_kmpElectrumClient {
	
	func notificationsSequence() -> AnyAsyncSequence<Lightning_kmpElectrumSubscriptionResponse> {
		
		return self.notifications
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

extension Lightning_kmpElectrumMiniWallet {
	
	func walletStateSequence() -> AnyAsyncSequence<Lightning_kmpWalletState> {
		
		return self.walletStateFlow
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

extension Lightning_kmpElectrumWatcher {
	
	func upToDateSequence() -> AnyAsyncSequence<Int64> {
			
		return self.openUpToDateFlow()
			.compactMap { $0 }
			.map { $0.int64Value }
			.eraseToAnyAsyncSequence()
	}
}

extension Lightning_kmpNodeParams {
	
	func nodeEventsSequence() -> AnyAsyncSequence<Lightning_kmpNodeEvents> {
			
		return self.nodeEvents
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

extension Lightning_kmpSwapInWallet {
	
	struct SwapInAddressInfo {
		let addr: String
		let index: Int
	}
	
	func swapInAddressSequence() -> AnyAsyncSequence<SwapInAddressInfo?> {
			
		return self.swapInAddressFlow
			.map {
				var result: SwapInAddressInfo? = nil
				if let pair = $0,
					let addr = pair.first as? String,
					let index = pair.second
				{
					result = SwapInAddressInfo(addr: addr, index: index.intValue)
				}
				return result
			}
			.eraseToAnyAsyncSequence()
	}
}
