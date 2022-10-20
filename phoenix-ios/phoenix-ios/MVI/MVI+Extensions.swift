import Foundation
import PhoenixShared


extension MVIState where Model: CloseChannelsConfiguration.Model, Intent: CloseChannelsConfiguration.Intent {
	
	func channels() -> [CloseChannelsConfiguration.ModelChannelInfo]? {
		
		if let model = self.model as? CloseChannelsConfiguration.ModelReady {
			return model.channels
		} else if let model = self.model as? CloseChannelsConfiguration.ModelChannelsClosed {
			return model.channels
		} else {
			return nil
		}
	}
	
	func balanceSats() -> Int64 {
		
		// Note that there's a subtle difference between
		// - global balance => sum of local millisatoshi amount in each open channel
		// - closing balance => some of local satoshi amount in each open channel
		//
		// When closing a channel, the extra millisatoshi amount gets truncated.
		// For this reason, there could be a small difference.
		
		if let channels = channels() {
			return channels.map { $0.balance }.reduce(0, +)
		} else {
			return 0
		}
	}
}
