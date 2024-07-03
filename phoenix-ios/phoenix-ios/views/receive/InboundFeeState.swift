import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "InboundFeeState"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class InboundFeeState: ObservableObject {
	
	@Published var connections: Connections = Biz.business.connectionsManager.currentValue
	@Published var channels: [LocalChannelInfo] = Biz.business.peerManager.channelsValue()
	@Published var liquidityPolicy: LiquidityPolicy = GroupPrefs.shared.liquidityPolicy
	@Published var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	private var cancellables = Set<AnyCancellable>()
	private var mempoolTask: Task<(), Error>? = nil
	
	init() {
		log.trace("init()")
		
		Biz.business.connectionsManager.connectionsPublisher()
			.sink {[weak self](newValue: Connections) in
				self?.connectionsChanged(newValue)
			}
			.store(in: &cancellables)
		
		Biz.business.peerManager.channelsPublisher()
			.sink {[weak self](newValue: [LocalChannelInfo]) in
				self?.channelsChanged(newValue)
			}
			.store(in: &cancellables)
		
		GroupPrefs.shared.liquidityPolicyPublisher
			.sink {[weak self](newValue: LiquidityPolicy) in
				self?.liquidityPolicyChanged(newValue)
			}
			.store(in: &cancellables)
		
		mempoolTask = Task { @MainActor in
			await fetchMempoolRecommendedFees()
		}
	}
	
	deinit {
		log.trace("deinit()")
		
		cancellables.removeAll()
		mempoolTask?.cancel()
	}
	
	private func connectionsChanged(_ newValue: Connections) {
		log.trace("connectionsChanged()")
		assertMainThread()
		
		self.connections = newValue
	}
	
	private func channelsChanged(_ newValue: [LocalChannelInfo]) {
		log.trace("channelsChanged()")
		assertMainThread()
		
		self.channels = newValue
	}
	
	private func liquidityPolicyChanged(_ newValue: LiquidityPolicy) {
		log.trace("liquidityPolicyChanged()")
		assertMainThread()
		
		self.liquidityPolicy = newValue
	}
	
	private func mempoolRecommendedResponseChanged(_ newValue: MempoolRecommendedResponse) {
		log.trace("mempoolRecommendedResponseChanged()")
		assertMainThread()
		
		self.mempoolRecommendedResponse = newValue
	}

	private func fetchMempoolRecommendedFees() async {
		
		for try await response in MempoolMonitor.shared.stream() {
			DispatchQueue.main.async {
				self.mempoolRecommendedResponseChanged(response)
			}
			if Task.isCancelled {
				return
			}
		}
	}
	
	func calculateInboundFeeWarning(
		invoiceAmount: Lightning_kmpMilliSatoshi?
	) -> InboundFeeWarning? {

		if !connections.peer.isEstablished() {
			return nil
		}
		
		let availableForReceiveMsat = channels.availableForReceive()?.msat ?? Int64(0)
		let hasNoLiquidity = availableForReceiveMsat == 0
		
		let canRequestLiquidity = channels.canRequestLiquidity()
		
		let invoiceAmountMsat = invoiceAmount?.msat
		
		var liquidityIsShort = false
		if let invoiceAmountMsat {
			liquidityIsShort = invoiceAmountMsat >= availableForReceiveMsat
		}
		
		if hasNoLiquidity || liquidityIsShort {
			
			if !liquidityPolicy.enabled {
				
				return InboundFeeWarning.liquidityPolicyDisabled
				
			} else {
				
				let hasNoChannels = channels.filter { !$0.isTerminated }.isEmpty
				let swapFeeSats = mempoolRecommendedResponse?.payToOpenEstimationFee(
					amount: Lightning_kmpMilliSatoshi(msat: invoiceAmountMsat ?? 0),
					hasNoChannels: hasNoChannels
				).sat
				
				if let swapFeeSats {
					
					// Check absolute fee
					
					if swapFeeSats > liquidityPolicy.effectiveMaxFeeSats
						&& !liquidityPolicy.effectiveSkipAbsoluteFeeCheck
					{
						return InboundFeeWarning.overAbsoluteFee(
							canRequestLiquidity: canRequestLiquidity,
							maxAbsoluteFeeSats: liquidityPolicy.effectiveMaxFeeSats,
							swapFeeSats: swapFeeSats
						)
					}
					
					// Check relative fee
					
					if let invoiceAmountMsat, invoiceAmountMsat > availableForReceiveMsat {
						
						let swapFeeMsat = Utils.toMsat(sat: swapFeeSats)
						
						let maxFeePercent = Double(liquidityPolicy.effectiveMaxFeeBasisPoints) / Double(10_000)
						let maxFeeMsat = Int64(Double(invoiceAmountMsat) * maxFeePercent)
						
						if swapFeeMsat > maxFeeMsat {
							return InboundFeeWarning.overRelativeFee(
								canRequestLiquidity: canRequestLiquidity,
								maxRelativeFeePercent: maxFeePercent,
								swapFeeSats: swapFeeSats
							)
						}
					}
				}
				
				if let swapFeeSats {
					return InboundFeeWarning.feeExpected(swapFeeSats: swapFeeSats)
				} else {
					return InboundFeeWarning.unknownFeeExpected
				}
				
			} // </else: liquidityPolicy.enabled>
		} // </if hasNoLiquidity || liquidityIsShort>
		
		return nil
	}
}
