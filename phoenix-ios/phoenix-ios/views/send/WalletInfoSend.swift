import SwiftUI
import PhoenixShared

fileprivate let filename = "WalletInfoSend"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WalletInfoSend: View {
	
	let metadata = SecurityFileManager.shared.currentWallet() ?? WalletMetadata.default()
	
	@State var channels: [LocalChannelInfo] = []
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@Environment(\.dismiss) var dismiss: DismissAction
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text(metadata.name)
				.lineLimit(3)
				.multilineTextAlignment(.leading)
				.font(.title3)
				.fontWeight(.medium)
				.foregroundStyle(.secondary)
			
			Divider()
				.padding(.top, 10)
				.padding(.bottom, 20)
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Wallet balance")
					
					let (btcValue, fiatValue) = walletBalanceValues()
					Text(btcValue.string)
						.lineLimit(2)
						.multilineTextAlignment(.trailing)
						.font(.callout)
						.foregroundColor(.primary)
						.fixedSize(horizontal: false, vertical: true) // text truncation bugs
					
					Text(verbatim: "≈ \(fiatValue.string)")
						.lineLimit(2)
						.multilineTextAlignment(.trailing)
						.font(.callout)
						.foregroundColor(.primary.opacity(0.6))
						.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				}
				
			} icon: {
				Image(systemName: "paperplane")
					.imageScale(.small)
			}
		}
		.padding()
		.task {
			for await value in Biz.business.peerManager.channelsArraySequence() {
				channels = value
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func walletBalanceValues() -> (FormattedAmount, FormattedAmount) {
		
		let msats = channels.map { channel in
			channel.localBalance?.msat ?? Int64(0)
		}.sum()
		
		let btcValue = Utils.formatBitcoin(currencyPrefs, msat: msats, policy: .showMsatsIfZeroSats)
		let fiatValue = Utils.formatFiat(currencyPrefs, msat: msats)
		
		return (btcValue, fiatValue)
	}
}

