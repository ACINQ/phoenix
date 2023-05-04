import SwiftUI
import SegmentedPicker
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ChannelsConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ChannelsConfigurationView: View {
	
	@State var sharing: String? = nil
	@State var showChannelsRemoteBalance = Prefs.shared.showChannelsRemoteBalance
	
	@StateObject var toast = Toast()
	
	let channelsPublisher = Biz.business.peerManager.channelsPublisher()
	@State var channels: [LocalChannelInfo] = []
	
	@ViewBuilder
	var body: some View {
		
		content()
			.sharing($sharing)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.onReceive(channelsPublisher) {
				channels = $0
			}
			.onDisappear {
				onDisappear()
			}
			.navigationTitle(NSLocalizedString("Payment channels", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		if (channels.isEmpty) {
			NoChannelsView(
				channels: $channels,
				sharing: $sharing,
				showChannelsRemoteBalance: $showChannelsRemoteBalance,
				toast: toast
			)
		} else {
			ChannelsView(
				channels: $channels,
				sharing: $sharing,
				showChannelsRemoteBalance: $showChannelsRemoteBalance,
				toast: toast
			)
		}
	}
	
	func onDisappear() {
		log.trace("onDisappear()")
		
		Prefs.shared.showChannelsRemoteBalance = showChannelsRemoteBalance
	}
}

fileprivate struct NoChannelsView : View {
	
	@Binding var channels: [LocalChannelInfo]
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	@ObservedObject var toast: Toast
	
	var body: some View {
		
		VStack {
			
			List {
				Section {
					VStack(alignment: HorizontalAlignment.center, spacing: 0) {
						Text("You don't have any payment channels.")
							.multilineTextAlignment(.center)
					}
				}
			}
			.listStyle(.insetGrouped)
			
			Spacer(minLength: 0)
			
			FooterView(
				showChannelsRemoteBalance: $showChannelsRemoteBalance,
				toast: toast
			)
		}
	}
}

fileprivate struct ChannelsView : View {
	
	@Binding var channels: [LocalChannelInfo]
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	@ObservedObject var toast: Toast
	
	@State var forceCloseChannelsOpen = false
	
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	var body: some View {
		
		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: forceCloseChannelsView(),
					isActive: $forceCloseChannelsOpen
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Color.primaryBackground.frame(height: 25)
				
				ScrollView {
					LazyVStack(pinnedViews: [.sectionHeaders]) {
						Section(header: header()) {
							ForEach(channels, id: \.channelId) { channel in
								row(channel: channel)
							}
						}
					}
				}
				
				FooterView(
					showChannelsRemoteBalance: $showChannelsRemoteBalance,
					toast: toast
				)
				
			} // </VStack>
			
			toast.view()
			
		} // </ZStack>
		.navigationBarItems(trailing: menuButton())
		.navigationStackDestination(isPresented: $forceCloseChannelsOpen) { // For iOS 16+
			forceCloseChannelsView()
		}
	}
	
	@ViewBuilder
	func forceCloseChannelsView() -> some View {
		ForceCloseChannelsView()
	}
	
	@ViewBuilder
	func menuButton() -> some View {
		
		Menu {
		//	Button {
		//		sharing = mvi.model.json
		//	} label: {
		//		Label {
		//			Text(verbatim: "Share all")
		//		} icon: {
		//			Image(systemName: "square.and.arrow.up")
		//		}
		//	}
			Button {
				closeAllChannels()
			} label: {
				Label {
					Text(verbatim: "Close all")
				} icon: {
					Image(systemName: "xmark.circle")
				}
			}
			if #available(iOS 15.0, *) {
				Button(role: .destructive) {
					forceCloseAllChannels()
				} label: {
					Label {
						Text(verbatim: "Force close all")
					} icon: {
						Image(systemName: "exclamationmark.triangle")
					}
				}
			} else {
				Button {
					forceCloseAllChannels()
				} label: {
					Label {
						Text(verbatim: "Force close all")
					} icon: {
						Image(systemName: "exclamationmark.triangle")
					}
				}
			}
			
		} label: {
			Image(systemName: "ellipsis")
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		ChannelHeaderView(
			channels: $channels,
			sharing: $sharing,
			showChannelsRemoteBalance: $showChannelsRemoteBalance
		)
	}
	
	@ViewBuilder
	func row(channel: LocalChannelInfo) -> some View {
		
		ChannelRowView(
			channel: channel,
			sharing: $sharing,
			showChannelsRemoteBalance: $showChannelsRemoteBalance,
			toast: toast
		)
	}
	
	func closeAllChannels() {
		log.trace("closeAllChannels()")
		
		presentationMode.wrappedValue.dismiss()
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
			deepLinkManager.broadcast(.drainWallet)
		}
	}
	
	func forceCloseAllChannels() {
		log.trace("forceCloseAllChannels()")
		
		forceCloseChannelsOpen = true
	}
}

struct ChannelHeaderView: View {
	
	@Binding var channels: [LocalChannelInfo]
	@Binding var sharing: String?
	@Binding var showChannelsRemoteBalance: Bool
	
	@Environment(\.colorScheme) var colorScheme
	
	var body: some View {
		HStack {
			
			if channels.count == 1 {
				Text("1 channel")
			} else {
				Text(String(format: NSLocalizedString(
					"%d channels",
					comment: "Count != 1"),
					channels.count
				))
			}
			
			Spacer()
			
			let (numerator, denominator) = balances()
			Text(verbatim: "\(numerator.digits) / \(denominator.digits) sat")
		}
		.frame(maxWidth: .infinity)
		.padding()
		.background(Color.primaryBackground)
	}
	
	func balances() -> (FormattedAmount, FormattedAmount) {
		
		let localMsats = channels.map { channel in
			channel.localBalance?.msat ?? Int64(0)
		}
		.reduce(into: Int64(0)) { result, next in
			result += next
		}
		let totalSats = channels.map { channel in
			channel.currentFundingAmount?.sat ?? Int64(0)
		}
		.reduce(into: Int64(0)) { result, next in
			result += next
		}
		
		let totalMsats = Utils.toMsat(sat: totalSats)
		
		let numerator_msats = localMsats
		let denominator_msats = showChannelsRemoteBalance ? (totalMsats - localMsats) : totalMsats
		
		let numerator = Utils.formatBitcoin(
			msat: numerator_msats,
			bitcoinUnit: .sat,
			policy: .showMsatsIfZeroSats
		)
		let denominator = Utils.formatBitcoin(
			msat: denominator_msats,
			bitcoinUnit: .sat,
			policy: .hideMsats
		)
		
		return (numerator, denominator)
	}
}

fileprivate struct ChannelRowView: View {
	
	let channel: LocalChannelInfo
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
						.foregroundColor(channel.isUsable ? .appPositive : .appNegative)
				
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
		
		guard let localMsats = channel.localBalance?.msat,
				let totalSats = channel.currentFundingAmount?.sat
		else {
			return nil
		}
		
		let totalMsats = Utils.toMsat(sat: totalSats)
		
		let numerator_msats = localMsats
		let denominator_msats = showChannelsRemoteBalance ? (totalMsats - localMsats) : totalMsats
		
		let numerator = Utils.formatBitcoin(
			msat: numerator_msats,
			bitcoinUnit: .sat,
			policy: .showMsatsIfZeroSats
		)
		let denominator = Utils.formatBitcoin(
			msat: denominator_msats,
			bitcoinUnit: .sat,
			policy: .hideMsats
		)
		
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
				
				let nodeId = Biz.nodeId ?? "?"
				Button {
					copyNodeID(nodeId)
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
						Text(nodeId)
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
				? UIColor.primaryBackground
				: UIColor.secondarySystemGroupedBackground
			)
			.edgesIgnoringSafeArea(.bottom)
		)
	}
	
	func copyNodeID(_ nodeID: String) {
		log.trace("[\(viewName)] copyNodeID")
		
		UIPasteboard.general.string = nodeID
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
}
