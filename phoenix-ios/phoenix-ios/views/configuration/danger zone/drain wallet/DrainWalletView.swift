import SwiftUI
import PhoenixShared

fileprivate let filename = "DrainWalletView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DrainWalletView: MVIView {
	
	enum NavLinkTag: String, Codable {
		case ConfirmView
	}
	
	@StateObject var mvi = MVIState({ $0.closeChannelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	let popTo: (PopToDestination) -> Void
	
	@State var didAppear = false

	@State var btcAddressInputResult: Result<BitcoinUri, BtcAddressInput.DetailedError> = .failure(.emptyInput)
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		layers()
			.navigationTitle(NSLocalizedString("Drain wallet", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
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
			if mvi.model is CloseChannelsConfiguration.ModelLoading {
				section_loading()
				
			} else if mvi.model is CloseChannelsConfiguration.ModelReady {
				section_balance()
				
				// What if the balance is zero ? Do we still display this ?
				// Yes, because the user could still have open channels (w/balance: zero local / non-zero remote),
				// and we want to allow them to close the channels if they desire.
				//
				section_options()
				section_button()
				
			} else if mvi.model is CloseChannelsConfiguration.ModelChannelsClosed {
				section_channelsClosed()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_loading() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle())
				Text("Loading wallet...")
			}
		} // </Section>
	}
	
	@ViewBuilder
	func section_channelsClosed() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				Image(systemName: "checkmark.circle.fill").foregroundColor(.appPositive)
				Text("Channels closed")
			}
		} // </Section>
	}
	
	@ViewBuilder
	func section_balance() -> some View {
		
		Section(header: Text("Wallet Balance")) {
			let (balance_bitcoin, balance_fiat) = formattedBalances()
			
			Group {
				if let balance_fiat = balance_fiat {
					Text(verbatim: balance_bitcoin.string).bold()
					+ Text(verbatim: " (≈ \(balance_fiat.string))")
						.foregroundColor(.secondary)
				} else {
					Text(verbatim: balance_bitcoin.string).bold()
				}
			} // </Group>
			.fixedSize(horizontal: false, vertical: true) // Workaround for SwiftUI bugs
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_options() -> some View {
		
		Section() {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Text("Send all funds to a Bitcoin wallet.")
					.foregroundColor(.primary)
					.fixedSize(horizontal: false, vertical: true)
				
				BtcAddressInput(result: $btcAddressInputResult)
				
				if case let .failure(reason) = btcAddressInputResult,
				   let errorMessage = reason.localizedErrorMessage()
				{
					Text(errorMessage)
						.foregroundColor(Color.appNegative)
				}
				
				Text("All payment channels will be closed.")
					.font(.footnote)
					.foregroundColor(.secondary)
				
			} // </VStack>
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 5) {
				
				Button {
					reviewButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Text("Review")
						Image(systemName: "arrow.forward")
							.imageScale(.small)
					}
				}
				.disabled(btcAddressInputResult.isError)
				.font(.title3.weight(.medium))
					
			} // </VStack>
			.frame(maxWidth: .infinity)
		}
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
		case .ConfirmView:
			if case .success(let bitcoinUri) = btcAddressInputResult {
				DrainWalletView_Confirm(
					mvi: mvi,
					bitcoinAddress: bitcoinUri.address,
					popTo: popToWrapper
				)
			} else {
				EmptyView()
			}
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
	
	func formattedBalances() -> (FormattedAmount, FormattedAmount?) {
		
		let balance_sats = mvi.balanceSats()
		
		let balance_bitcoin = Utils.formatBitcoin(sat: balance_sats, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var balance_fiat: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			balance_fiat = Utils.formatFiat(sat: balance_sats, exchangeRate: exchangeRate)
		}
		
		return (balance_bitcoin, balance_fiat)
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
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if let deepLink = deepLinkManager.deepLink, deepLink == .drainWallet {
				// Reached our destination
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkManager.unbroadcast(deepLink)
				}
			}
			
		} else {
			
			if popToDestination != nil {
				popToDestination = nil
				presentationMode.wrappedValue.dismiss()
			}
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
	
	func reviewButtonTapped() {
		log.trace("reviewButtonTapped()")
		
		navigateTo(.ConfirmView)
	}
}
