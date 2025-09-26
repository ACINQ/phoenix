import SwiftUI
import PhoenixShared

fileprivate let filename = "DrainWalletView_Confirm"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DrainWalletView_Confirm: MVISubView {
	
	enum NavLinkTag: String, Codable {
		case ActionView
	}

	@ObservedObject var mvi: MVIState<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
	let bitcoinAddress: String
	let minerFeeInfo: MinerFeeInfo
	
	let popTo: (PopToDestination) -> Void
	
	@State var actionFired: Bool = false
	@State var expectedTxCount: Int = 0
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Confirm Closing", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) {
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
		}
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_notices()
			section_button()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		let balance_info = formattedBalanceString()
		let fees_info = formattedFeesString()
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Text("Your balance of:")
					.padding(.bottom, 5)
				
				Text(verbatim: balance_info)
					.font(.body.weight(.semibold))
					.padding(.bottom, 15)
				
				Text("Minus miner fees estimated to:")
					.padding(.bottom, 5)
				
				Text(verbatim: fees_info)
					.font(.body.weight(.semibold))
					.padding(.bottom, 15)
				
				Text("will be sent to:")
					.padding(.bottom, 5)
				
				Text(verbatim: bitcoinAddress)
					.font(.system(.callout, design: .monospaced).weight(.semibold))
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			.multilineTextAlignment(.center)
			.padding(.vertical, 5)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_notices() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					Text("Please ensure the address is correct")
				} icon: {
					Image(systemName: "eye")
				}
				.font(.callout)
				.padding(.bottom, 15)
				
				Label {
					Text("The transaction may take 30 minutes or more to confirm on the bitcoin blockchain")
				} icon: {
					Image(systemName: "tortoise")
				}
				.font(.callout)
				.foregroundColor(.secondary)
				
			} // </VStack>
			.padding(.vertical, 5)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					drainWallet()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "bitcoinsign.circle")
							.imageScale(.medium)
						Text("Drain my wallet")
					}
					.font(.headline)
					.foregroundColor(.appNegative)
				}
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .ActionView:
			DrainWalletView_Action(
				mvi: mvi,
				expectedTxCount: expectedTxCount,
				popTo: popToWrapper
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	func formattedBalanceString() -> String {
		
		let balance_sats = mvi.balanceSats()
		
		let balance_bitcoin = Utils.formatBitcoin(sat: balance_sats, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var balance_fiat: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			balance_fiat = Utils.formatFiat(sat: balance_sats, exchangeRate: exchangeRate)
		}
		
		if let balance_fiat {
			return "\(balance_bitcoin.string) (≈ \(balance_fiat.string))"
		} else {
			return balance_bitcoin.string
		}
	}
	
	func formattedFeesString() -> String {
		
		let fee_sats = minerFeeInfo.minerFee
		
		let fee_bitcoin = Utils.formatBitcoin(sat: fee_sats, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var fee_fiat: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			fee_fiat = Utils.formatFiat(sat: fee_sats, exchangeRate: exchangeRate)
		}
		
		if let fee_fiat {
			return "\(fee_bitcoin.string) (≈ \(fee_fiat.string))"
		} else {
			return fee_bitcoin.string
		}
	}
	
	func nonZeroChannelCount() -> Int {
		
		if let channels = mvi.channels() {
			return channels.filter { ($0.balance?.sat ?? 0) > 0 }.count
		} else {
			return 0
		}
	}
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popToWrapper(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
			popTo(destination)
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if popToDestination != nil {
			popToDestination = nil
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func drainWallet() {
		log.trace("drainWallet()")
		
		if !actionFired {
			actionFired = true
			expectedTxCount = nonZeroChannelCount()
			navigateTo(.ActionView)
			mvi.intent(CloseChannelsConfiguration.IntentMutualCloseAllChannels(
				address: bitcoinAddress,
				feerate: minerFeeInfo.feerate
			))
		}
	}
}
