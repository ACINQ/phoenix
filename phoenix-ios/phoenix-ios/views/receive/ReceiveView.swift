import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ReceiveView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum SelectedTab {
	case lightning
	case blockchain
}

struct ReceiveView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var selectedTab: SelectedTab = .lightning
	
	@State var lastDescription: String? = nil
	@State var lastAmount: Lightning_kmpMilliSatoshi? = nil
	
	@State var receiveLightningView_didAppear = false
	
	@State var swapIn_enabled = true
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			customTabBar()
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
	}
	
	@ViewBuilder
	func customTabBar() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			switch selectedTab {
			case .lightning:
				ReceiveLightningView(
					mvi: mvi,
					toast: toast,
					didAppear: $receiveLightningView_didAppear
				)
				
			case .blockchain:
				SwapInView(
					mvi: mvi,
					toast: toast,
					lastDescription: $lastDescription,
					lastAmount: $lastAmount
				)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 20) {
				
				Button {
					didSelectTab(.lightning)
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "bolt").font(.title2).imageScale(.large)
						VStack(alignment: HorizontalAlignment.center, spacing: 4) {
							Text("Lightning").padding(.bottom, 4)
							Text("(layer 2)").font(.footnote.weight(.thin)).opacity(0.7)
						}
					}
				}
				.foregroundColor(selectedTab == .lightning ? Color.appAccent : Color.primary)
				.frame(maxWidth: .infinity)
				
				Button {
					didSelectTab(.blockchain)
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "link").font(.title2).imageScale(.large)
						VStack(alignment: HorizontalAlignment.center, spacing: 0) {
							Text("Blockchain").padding(.bottom, 4)
							Text("(layer 1)").font(.footnote.weight(.thin)).opacity(0.7)
						}
					}
				}
				.foregroundColor(selectedTab == .blockchain ? Color.appAccent : Color.primary)
				.frame(maxWidth: .infinity)
				
			} // </HStack>
			.padding(.top, 15)
			.background(
				Color.mutedBackground
					.edgesIgnoringSafeArea(.bottom) // background color should extend to bottom of screen
			)
			
		} // </VStack>
		
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
			lastDescription = m.desc
			lastAmount = m.amount
		}
	}
	
	func didSelectTab(_ tab: SelectedTab) {
		
		switch tab {
		case .lightning:
			mvi.intent(Receive.IntentAsk(
				amount: lastAmount,
				desc: lastDescription,
				expirySeconds: Prefs.shared.invoiceExpirationSeconds
			))
			
		case .blockchain:
			if swapIn_enabled {
				mvi.intent(Receive.IntentRequestSwapIn())
			} else {
				popoverState.display(dismissable: true) {
					SwapInDisabledPopover()
				}
			}
		}
		
		selectedTab = tab
	}
	
	// --------------------------------------------------
	// MARK: Static Shared
	// --------------------------------------------------
	
	/// Shared logic. Used by:
	/// - ReceiveLightningView
	/// - SwapInView
	///
	static func qrCodeBorderColor(_ colorScheme: ColorScheme) -> Color {
		
		return (colorScheme == .dark) ? Color(UIColor.separator) : Color.appAccent
	}
}

// MARK: -

class ReceiveView_Previews: PreviewProvider {

	static let request = "lntb17u1p0475jgpp5f69ep0f2202rqegjeddjxa3mdre6ke6kchzhzrn4rxzhyqakzqwqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp2xqrrss9qy9qsqsp5nhhdgpz3549mll70udxldkg48s36cj05epp2cjjv3rrvs5hptdfqlq6h3tkkaplq4au9tx2k49tcp3gx7azehseq68jums4p0gt6aeu3gprw3r7ewzl42luhc3gyexaq37h3d73wejr70nvcw036cde4ptgpckmmkm"

	static var previews: some View {

		NavigationWrapper {
			ReceiveView().mock(Receive.Model_Awaiting())
		}
		.modifier(GlobalEnvironment())
		.previewDevice("iPhone 11")

		NavigationWrapper {
			ReceiveView().mock(Receive.Model_Generated(
				request: request,
				paymentHash: "foobar",
				amount: Lightning_kmpMilliSatoshi(msat: 170000),
				desc: "1 Espresso Coin Panna"
			))
		}
		.modifier(GlobalEnvironment())
		.previewDevice("iPhone 11")
	}
}
