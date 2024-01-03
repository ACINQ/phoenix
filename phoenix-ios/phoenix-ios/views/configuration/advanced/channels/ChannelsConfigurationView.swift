import SwiftUI
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
	
	let channelsPublisher = Biz.business.peerManager.channelsPublisher()
	@State var channels: [LocalChannelInfo] = []
	
	@State var importChannelsOpen = false
	
	enum CapacityHeight: Preference {}
	let capacityHeightReader = GeometryPreferenceReader(
		key: AppendValue<CapacityHeight>.self,
		value: { [$0.size.height] }
	)
	@State var capacityHeight: CGFloat? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.sharing($sharing)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.onReceive(channelsPublisher) {
				channels = $0
			}
			.navigationTitle(NSLocalizedString("Payment channels", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: importChannelsView(),
					isActive: $importChannelsOpen
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			List {
				if channels.isEmpty {
					section_noChannels()
				} else {
					section_balance()
					section_channels()
				}
			}
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
			
			toast.view()
			
		} // </ZStack>
		.navigationBarItems(trailing: menuButton())
		.navigationStackDestination(isPresented: $importChannelsOpen) { // For iOS 16+
			importChannelsView()
		}
	}
	
	@ViewBuilder
	func section_noChannels() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				Text("You don't have any payment channels.")
					.multilineTextAlignment(.center)
			}
		}
	}
	
	@ViewBuilder
	func section_balance() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "square.fill")
						.font(.subheadline)
						.imageScale(.small)
						.foregroundColor(localBalanceColor())
					Text("Balance")
					Spacer(minLength: 0)
					Text("Inbound Liquidity")
					Image(systemName: "square.fill")
						.imageScale(.small)
						.font(.subheadline)
						.foregroundColor(remoteBalanceColor())
				}
				
				let (localMsats, remoteMsats) = localAndRemoteMsats()
				
				ProgressView(value: Double(localMsats), total: Double(localMsats + remoteMsats))
					.tint(localBalanceColor())
					.background(remoteBalanceColor())
					.padding(.vertical, 4)
				
				let localBitcoin = Utils.formatBitcoin(
					msat: localMsats,
					bitcoinUnit: .sat,
					policy: .showMsatsIfZeroSats
				)
				let remoteBitcoin = Utils.formatBitcoin(
					msat: remoteMsats,
					bitcoinUnit: .sat,
					policy: .showMsatsIfZeroSats
				)
				
				HStack(alignment: VerticalAlignment.center, spacing: 2) {
					Text(localBitcoin.string)
					Spacer(minLength: 0)
					Text(remoteBitcoin.string)
				}
				.font(.callout)
				.foregroundColor(.primary.opacity(0.8))
				
				let localFiat = Utils.formatFiat(currencyPrefs, msat: localMsats)
				let remoteFiat = Utils.formatFiat(currencyPrefs, msat: remoteMsats)
				
				HStack(alignment: VerticalAlignment.center, spacing: 2) {
					Text("≈\(localFiat.string)")
					Spacer(minLength: 0)
					Text("≈\(remoteFiat.string)")
				}
				.font(.callout)
				.foregroundColor(.primary.opacity(0.6))
				
				subsection_capacity(localMsats + remoteMsats)
			}
			.padding(.vertical, 4)
			
		} header: {
			Text("Balance")
		}
	}
	
	@ViewBuilder
	func subsection_capacity(_ capacityMsats: Int64) -> some View {
		
		let dividerThickness = CGFloat(2)
		let dividerColor = Color(UIColor.systemGray5)
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.bottom, spacing: 2) {
				Divider()
					.frame(width: dividerThickness, height: capacityHeight ?? 8)
					.overlay(dividerColor)
				Spacer(minLength: 0)
				Text("Capacity")
					.padding(.bottom, 2)
					.read(capacityHeightReader)
				Spacer(minLength: 0)
				Divider()
					.frame(width: dividerThickness, height: capacityHeight ?? 8)
					.overlay(dividerColor)
			}
			.assignMaxPreference(for: capacityHeightReader.key, to: $capacityHeight)
			
			Divider()
				.frame(height: dividerThickness)
				.overlay(dividerColor)
			
			let capacityBitcoin = Utils.formatBitcoin(msat: capacityMsats, bitcoinUnit: .sat, policy: .hideMsats)
			let capacityFiat = Utils.formatFiat(currencyPrefs, msat: capacityMsats)
			
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
			
				Text(capacityBitcoin.string)
					.foregroundColor(.primary.opacity(0.8))
				
				Text("|")
					.foregroundColor(.primary.opacity(0.7))
				
				Text("≈\(capacityFiat.string)")
					.foregroundColor(.primary.opacity(0.6))
			}
			.font(.callout)
			.padding(.top, 2)
			
		} // </VStack>
		.padding(.horizontal, 8)
		.padding(.top, 4)
	}
	
	@ViewBuilder
	func section_channels() -> some View {
		
		Section {
			ForEach(channels, id: \.channelId) { channel in
				row_channel(channel)
			}
			
		} header: {
			Text("Payment Channels")
		}
	}
	
	@ViewBuilder
	func row_channel(_ channel: LocalChannelInfo) -> some View {
		
		Button {
			showChannelInfoPopover(channel)
		} label: {
		
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				Image("ic_bullet")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 10, height: 10)
					.foregroundColor(channel.isUsable ? .appPositive : .appNegative)
			
				Text(channel.stateName)
					.foregroundColor(Color.primary)
				
				Spacer()
				
				if let tuple = channelBalances(channel) {
					Text(verbatim: "\(tuple.0.digits) / \(tuple.1.digits) sat")
						.foregroundColor(Color.primary)
				}
			} // </HStack>
		//	.padding([.leading], 10)
			.padding([.top, .bottom], 8)
		//	.padding(.trailing)
		} // </Button>
	}
	
	@ViewBuilder
	func menuButton() -> some View {
		
		Menu {
			Button {
				importChannels()
			} label: {
				Label {
					Text("Import channels")
				} icon: {
					Image(systemName: "square.and.arrow.down")
				}
			}
			if !channels.isEmpty {
				Button {
					closeAllChannels()
				} label: {
					Label {
						Text(verbatim: "Close all")
					} icon: {
						Image(systemName: "xmark.circle")
					}
				}
				Button(role: .destructive) {
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
	func importChannelsView() -> some View {
		ImportChannelsView()
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func localBalanceColor() -> Color {
		if BusinessManager.isTestnet {
			return Color.appAccentTestnet
		} else {
			return Color.appAccentMainnet
		}
	}
	
	func remoteBalanceColor() -> Color {
		return Color.appAccentOrange
	}
	
	func localAndRemoteMsats() -> (Int64, Int64) {
		
		let localMsats = channels.map { channel in
			channel.localBalance?.msat ?? Int64(0)
		}.sum()
		
		let remoteMsats = channels.map { channel in
			channel.availableForReceive?.msat ?? Int64(0)
		}.sum()
		
		log.debug("localMsats(\(localMsats)), remoteMsats(\(remoteMsats))")
		return (localMsats, remoteMsats)
	}
	
	func channelBalances(_ channel: LocalChannelInfo) -> (FormattedAmount, FormattedAmount)? {
		
		guard let localMsats = channel.localBalance?.msat,
				let remoteMsats = channel.availableForReceive?.msat
		else {
			return nil
		}
		
		let local = Utils.formatBitcoin(
			msat: localMsats,
			bitcoinUnit: .sat,
			policy: .showMsatsIfZeroSats
		)
		let remote = Utils.formatBitcoin(
			msat: remoteMsats,
			bitcoinUnit: .sat,
			policy: .showMsatsIfZeroSats
		)
		
		return (local, remote)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func showChannelInfoPopover(_ channel: LocalChannelInfo) {
		log.trace("showChannelInfoPopover()")
		
		popoverState.display(dismissable: true) {
			ChannelInfoPopup(
				channel: channel,
				sharing: $sharing,
				toast: toast
			)
		}
	}
	
	func importChannels() {
		log.trace("importChannels()")
		
		importChannelsOpen = true
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
		
		presentationMode.wrappedValue.dismiss()
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
			deepLinkManager.broadcast(.forceCloseChannels)
		}
	}
}
