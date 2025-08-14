import SwiftUI
import PhoenixShared

fileprivate let filename = "WalletInfoReceive"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WalletInfoReceive: View {
	
	let didTapScanQrCodeButton: () -> Void
	
	let metadata = SecurityFileManager.shared.currentWallet() ?? WalletMetadata.default()
	
	let channelsPublisher = Biz.business.peerManager.channelsPublisher()
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
					Text("Inbound liquidity")
					
					let (btcValue, fiatValue) = inboundLiquidityValues()
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
				if #available(iOS 17, *) {
					Image("bucket_monochrome_symbol")
				} else {
					Image("bucket_monochrome")
						.renderingMode(.template)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: 20, height: 20)
						.foregroundColor(.primary)
				}
			}
			
			Divider()
				.padding(.top, 20)
				.padding(.bottom, 10)
			
			Button {
				dismiss()
				didTapScanQrCodeButton()
			} label: {
				Label("Scan QR code", systemImage: "qrcode.viewfinder")
			}
			
		}
		.padding()
		.onReceive(channelsPublisher) {
			channels = $0
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func inboundLiquidityValues() -> (FormattedAmount, FormattedAmount) {
		
		let msats = channels.map { channel in
			channel.availableForReceive?.msat ?? Int64(0)
		}.sum()
		
		let btcValue = Utils.formatBitcoin(currencyPrefs, msat: msats, policy: .showMsatsIfZeroSats)
		let fiatValue = Utils.formatFiat(currencyPrefs, msat: msats)
		
		return (btcValue, fiatValue)
	}
}
