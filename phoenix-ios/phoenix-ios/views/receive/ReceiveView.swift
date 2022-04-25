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


struct ReceiveView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@State var lastDescription: String? = nil
	@State var lastAmount: Lightning_kmpMilliSatoshi? = nil
	
	@State var receiveLightningView_didAppear = false
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@StateObject var toast = Toast()
	
	// MARK: ViewBuilders
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			if mvi.model is Receive.Model_SwapIn {
				
				// Receive.Model_SwapIn_Requesting : Receive.Model_SwapIn
				// Receive.Model_SwapIn_Generated : Receive.Model_SwapIn
				
				SwapInView(
					mvi: mvi,
					toast: toast,
					lastDescription: $lastDescription,
					lastAmount: $lastAmount
				)
				
			} else {
			
				ReceiveLightningView(
					mvi: mvi,
					toast: toast,
					didAppear: $receiveLightningView_didAppear
				)
			}
			
			toast.view()
			
		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
	}
	
	// MARK: Actions
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
			lastDescription = m.desc
			lastAmount = m.amount
		}
	}
	
	// MARK: Static Shared
	
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

		NavigationView {
			ReceiveView().mock(Receive.Model_Awaiting())
		}
		.modifier(GlobalEnvironment())
		.previewDevice("iPhone 11")

		NavigationView {
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
