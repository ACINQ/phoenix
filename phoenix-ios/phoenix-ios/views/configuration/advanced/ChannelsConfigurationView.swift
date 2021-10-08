import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ChannelsConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ChannelsConfigurationView: MVIView {

	@StateObject var mvi = MVIState({ $0.channelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var sharing: String? = nil
	@State var showChannelsRemoteBalance = Prefs.shared.showChannelsRemoteBalance
	
	@StateObject var toast = Toast()
	
	@ViewBuilder
	var view: some View {
		
		Group {
			if (mvi.model.channels.isEmpty) {
				NoChannelsView(
					mvi: mvi,
					sharing: $sharing,
					showChannelsRemoteBalance: $showChannelsRemoteBalance,
					toast: toast
				)
			} else {
				ChannelsView(
					mvi: mvi,
					sharing: $sharing,
					showChannelsRemoteBalance: $showChannelsRemoteBalance,
					toast: toast
				)
			}
		}
		.sharing($sharing)
		.onDisappear {
			onDisappear()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle(
			NSLocalizedString("Payment channels", comment: "Navigation bar title"),
			displayMode: .inline
		)
	}
	
	func onDisappear() {
		log.trace("onDisappear()")
		
		Prefs.shared.showChannelsRemoteBalance = showChannelsRemoteBalance
	}
}

fileprivate struct NoChannelsView : View {
	
	@ObservedObject var mvi: MVIState<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	@ObservedObject var toast: Toast
	
	var body: some View {
		
		VStack {
			Text("You don't have any payment channels.")
				.padding()
			
			Spacer()
			FooterView(
				mvi: mvi,
				showChannelsRemoteBalance: $showChannelsRemoteBalance,
				toast: toast
			)
		}
	}
}

fileprivate struct ChannelsView : View {
	
	@ObservedObject var mvi: MVIState<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	@ObservedObject var toast: Toast
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		ZStack {
			
			VStack {
				
				ScrollView {
					LazyVStack(pinnedViews: [.sectionHeaders]) {
						Section(header: header()) {
							ForEach(mvi.model.channels, id: \.id) { channel in
								row(channel: channel)
							}
						}
					}
					
				} // </ScrollView>
				
				FooterView(
					mvi: mvi,
					showChannelsRemoteBalance: $showChannelsRemoteBalance,
					toast: toast
				)
				
			} // </VStack>
			
			toast.view()
			
		} // </ZStack>
		.navigationBarItems(trailing: shareChannelsButton())
	}
	
	@ViewBuilder
	func shareChannelsButton() -> some View {
		
		Button {
			sharing = mvi.model.json
		} label: {
			Image(systemName: "square.and.arrow.up")
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		ChannelHeaderView(
			mvi: mvi,
			sharing: $sharing,
			showChannelsRemoteBalance: $showChannelsRemoteBalance
		)
	}
	
	@ViewBuilder
	func row(channel: ChannelsConfiguration.ModelChannel) -> some View {
		
		ChannelRowView(
			channel: channel,
			sharing: $sharing,
			showChannelsRemoteBalance: $showChannelsRemoteBalance,
			toast: toast
		)
	}
}

struct ChannelHeaderView: View {
	
	@ObservedObject var mvi: MVIState<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	
	@Environment(\.colorScheme) var colorScheme
	
	var body: some View {
		HStack {
			
			if mvi.model.channels.count == 1 {
				Text("1 channel")
			} else {
				Text(String(format: NSLocalizedString(
					"%d channels",
					comment: "Count != 1"),
					mvi.model.channels.count
				))
			}
			
			Spacer()
			
			let (numerator, denominator) = balances()
			Text(verbatim: "\(numerator.digits) / \(denominator.digits) sat")
		}
		.frame(maxWidth: .infinity)
		.padding()
		.background(
			Color(
				colorScheme == ColorScheme.light
				? UIColor.systemGroupedBackground
				: UIColor.secondarySystemGroupedBackground
			)
		)
	}
	
	func balances() -> (FormattedAmount, FormattedAmount) {
		
		let localSats = mvi.model.channels.map { channel in
			channel.localBalance?.sat ?? Int64(0)
		}
		.reduce(into: Int64(0)) { result, next in
			result += next
		}
		let remoteSats = mvi.model.channels.map { channel in
			channel.remoteBalance?.sat ?? Int64(0)
		}
		.reduce(into: Int64(0)) { result, next in
			result += next
		}
		
		let numerator_sats = localSats
		let denominator_sats = showChannelsRemoteBalance ? remoteSats : (localSats + remoteSats)
		
		let numerator = Utils.formatBitcoin(sat: numerator_sats, bitcoinUnit: .sat)
		let denominator = Utils.formatBitcoin(sat: denominator_sats, bitcoinUnit: .sat)
		
		return (numerator, denominator)
	}
}

fileprivate struct ChannelRowView: View {
	
	let channel: ChannelsConfiguration.ModelChannel
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	@ObservedObject var toast: Toast
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack {
			
			Button {
				showChannelInfoPopover()
			} label: {
			
				HStack {
					Image("ic_bullet")
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: 10, height: 10)
						.foregroundColor(channel.isOk ? .appPositive : .appNegative)
				
					Text(channel.stateName)
						.foregroundColor(Color.primary)
					
					Spacer()
					
					if let tuple = balances() {
						Text(verbatim: "\(tuple.0.digits) / \(tuple.1.digits) sat")
							.foregroundColor(Color.primary)
					}
				}
				.padding([.leading], 10)
				.padding([.top, .bottom], 8)
				.padding(.trailing)
			}
			
			Divider()
		}
	}
	
	func balances() -> (FormattedAmount, FormattedAmount)? {
		
		guard let localSats = channel.localBalance?.sat,
				let remoteSats = channel.remoteBalance?.sat
		else {
			return nil
		}
		
		let numerator_sats = localSats
		let denominator_sats = showChannelsRemoteBalance ? remoteSats : (localSats + remoteSats)
		
		let numerator = Utils.formatBitcoin(sat: numerator_sats, bitcoinUnit: .sat)
		let denominator = Utils.formatBitcoin(sat: denominator_sats, bitcoinUnit: .sat)
		
		return (numerator, denominator)
	}
	
	func showChannelInfoPopover() {
		log.trace("showChannelInfoPopover()")
		
		popoverState.display(dismissable: true) {
			ChannelInfoPopup(
				channel: channel,
				sharing: $sharing,
				toast: toast
			)
		}
	}
}

fileprivate struct FooterView: View, ViewName {
	
	@ObservedObject var mvi: MVIState<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>
	@Binding var showChannelsRemoteBalance: Bool
	@ObservedObject var toast: Toast
	
	@Environment(\.colorScheme) var colorScheme
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 12) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				Text("Channel Balance Display:")
					.font(.footnote)
					.padding(.trailing, 4)
				
				Spacer()
				
				Button {
					showChannelsRemoteBalance.toggle()
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
						if showChannelsRemoteBalance {
							Text("local / remote")
						} else {
							Text("local / total")
						}
						Image(systemName: "arrow.2.squarepath")
							.imageScale(.medium)
					}
					.font(.footnote)
				}
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				Text("Your Node ID:")
					.font(.footnote)
					.padding(.trailing, 4)
				
				Spacer()
				
				Button {
					copyNodeID(mvi.model.nodeId)
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
						Text(mvi.model.nodeId)
							.lineLimit(1)
							.truncationMode(.middle)
						
						Image(systemName: "square.on.square")
							.imageScale(.medium)
					}
					.font(.footnote)
				}
			} // </HStack>
			
		} // </VStack>
		.frame(maxWidth: .infinity, alignment: .leading)
		.padding([.top, .bottom], 10)
		.padding([.leading, .trailing])
		.background(
			Color(
				colorScheme == ColorScheme.light
				? UIColor.systemGroupedBackground
				: UIColor.secondarySystemGroupedBackground
			)
			.edgesIgnoringSafeArea(.bottom)
		)
	}
	
	func copyNodeID(_ nodeID: String) {
		log.trace("[\(viewName)] copyNodeID")
		
		UIPasteboard.general.string = nodeID
		toast.pop(
			Text("Copied to pasteboard!").anyView,
			colorScheme: colorScheme.opposite
		)
	}
}

fileprivate struct ChannelInfoPopup: View, ViewName {
	
	let channel: ChannelsConfiguration.ModelChannel
	@Binding var sharing: String?
	@ObservedObject var toast: Toast
	
	@State var showBlockchainExplorerOptions = false
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack {
			ScrollView {
				Text(channel.json)
					.font(.caption)
					.padding()
			}
			.frame(height: 300)
			.frame(maxWidth: .infinity, alignment: .leading)
			
			HStack {
				Button {
					UIPasteboard.general.string = channel.json
					toast.pop(
						Text("Copied to pasteboard!").anyView,
						colorScheme: colorScheme.opposite
					)
				} label: {
					Image(systemName: "square.on.square")
						.resizable()
						.scaledToFit()
						.frame(width: 22, height: 22)
				}

				Divider()
					.frame(height: 30)
					.padding([.leading, .trailing], 8)

				Button {
					sharing = channel.json
				} label: {
					Image(systemName: "square.and.arrow.up")
						.resizable()
						.scaledToFit()
						.frame(width: 22, height: 22)
				}

				if let txId = channel.txId {
					Divider()
						.frame(height: 30)
						.padding([.leading, .trailing], 8)

					if #available(iOS 15.0, *) {
						Button {
							showBlockchainExplorerOptions = true
						} label: {
							Text("Tx").font(.title2)
						}
						.confirmationDialog("Blockchain Explorer",
							isPresented: $showBlockchainExplorerOptions,
							titleVisibility: .automatic
						) {
							Button("Mempool.space") {
								exploreTx(txId: txId, website: BlockchainExplorer.WebsiteMempoolSpace())
							}
							Button("Blockstream.info") {
								exploreTx(txId: txId, website: BlockchainExplorer.WebsiteBlockstreamInfo())
							}
							Button("Copy transaction id") {
								copyTxId(txId: txId)
							}
						}
					} else { // same functionality as before
						Button {
							exploreTx(txId: txId, website: BlockchainExplorer.WebsiteMempoolSpace())
						} label: {
							Text("Tx").font(.title2)
						}
					}
				}

				Spacer()
				Button("OK") {
					closePopover()
				}
				.font(.title2)
			}
			.padding(.top, 10)
			.padding([.leading, .trailing])
			.padding(.bottom, 10)
			.background(
				Color(UIColor.secondarySystemBackground)
			)
			
		} // </VStack>
	}
	
	func exploreTx(txId: String, website: BlockchainExplorer.Website) {
		log.trace("[\(viewName)] exploreTx()")
		
		let business = AppDelegate.get().business
		let txUrlStr = business.blockchainExplorer.txUrl(txId: txId, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	func copyTxId(txId: String) {
		log.trace("[\(viewName)] copyTxId()")
		
		UIPasteboard.general.string = txId
	}
	
	func closePopover() -> Void {
		log.trace("[\(viewName)] closePopover()")
		
		popoverState.close()
	}
}


// MARK:-

class ChannelsConfigurationView_Previews : PreviewProvider {
	
	static let channel1 = ChannelsConfiguration.ModelChannel(
		id: "b50bf19d16156de8231f6d3d3fb3dd105ba338de5366d0421b0954b9ceb0d4f8",
		isOk: true,
		stateName: "Normal",
		localBalance: Bitcoin_kmpSatoshi(sat: 50_000),
		remoteBalance: Bitcoin_kmpSatoshi(sat: 200_000),
		json: "{Everything is normal!}",
		txId: nil
	)
	
	static let channel2 = ChannelsConfiguration.ModelChannel(
		id: "e5366d0421b0954b9ceb0d4f8b50bf19d16156de8231f6d3d3fb3dd105ba338d",
		isOk: false,
		stateName: "Woops",
		localBalance: Bitcoin_kmpSatoshi(sat: 0),
		remoteBalance: Bitcoin_kmpSatoshi(sat: 0),
		json: "{Woops!}",
		txId: nil
	)

	static var previews: some View {
		
		NavigationView {
			ChannelsConfigurationView().mock(ChannelsConfiguration.Model(
				nodeId: "03af0ed6052cf28d670665549bc86f4b721c9fdb309d40c58f5811f63966e005d0",
				json: "{}",
				channels: []
			))
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")

		NavigationView {
			ChannelsConfigurationView().mock(ChannelsConfiguration.Model(
				nodeId: "03af0ed6052cf28d670665549bc86f4b721c9fdb309d40c58f5811f63966e005d0",
				json: "{}",
				channels: [channel1, channel2]
			))
		}
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
	}

	#if DEBUG
	@objc class func injected() {
		UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
	}
	#endif
}
