import Foundation
import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "IntroView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct IntroView: View {
	
	let finish: () -> Void
	
	@State private var selectedPage = 0
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.get().business.chain.isTestnet() {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			TabView(selection: $selectedPage) {
				
				IntroView1(advance: advance)
					.tag(0)
				
				IntroView2(advance: advance)
					.tag(1)
				
				IntroView3(finish: finish)
					.tag(2)
			}
			.tabViewStyle(PageTabViewStyle())
			.indexViewStyle(PageIndexViewStyle(backgroundDisplayMode: .always))
			.padding(.bottom, 40)
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
	}
	
	func advance() -> Void {
		log.trace("advance()")
		
		withAnimation {
			selectedPage += 1
		}
	}
}

struct IntroView1: View {
	
	let advance: () -> Void
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Welcome!")
				.font(.title)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 25) {
				Text("With Phoenix, receiving and sending bitcoin is safe, easy, and fast.")
			}
			.multilineTextAlignment(.center)
			.padding(.top, 30)
			.padding(.horizontal, 20) // 20+20=40
			.padding(.bottom, 40)
			
			Button {
				advance()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text("Next")
					Image(systemName: "arrow.right")
						.imageScale(.medium)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
			}
			.buttonStyle(
				ScaleButtonStyle(
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent,
					disabledBorderStroke: Color(UIColor.separator)
				)
			)
		}
		.padding(.horizontal)
		.offset(x: 0, y: -40) // move center upwards
	}
}

struct IntroView2: View {
	
	let advance: () -> Void
	
	@State var payToOpen_feePercent: Double = 0.0
	@State var payToOpen_minFeeSat: Int64 = 0
	
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Automatic Channel Creation")
				.font(.title)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 25) {
				
				Text("Payment channels are automatically created when needed.")
				
				let percent = formatFeePercent()
				let min = Utils.formatBitcoin(sat: payToOpen_minFeeSat, bitcoinUnit: .sat)
				
				Text(styled: NSLocalizedString(
					"The fee is **\(percent)%** with a minimum fee of **\(min.string)**.",
					comment: "IntroView"
				))
				
				Text(
					"""
					This fee only applies when a new channel needs to be created. \
					Payments that use existing channels don't pay this fee. \
					The fee is dynamic and may change depending on bitcoin network conditions.
					"""
				)
				.font(.footnote)
			}
			.multilineTextAlignment(.center)
			.padding(.top, 30)
			.padding(.horizontal, 20) // 20+20=40
			.padding(.bottom, 40)
			
			Button {
				advance()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text("I understand")
					Image(systemName: "arrow.right")
						.imageScale(.medium)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
			}
			.buttonStyle(
				ScaleButtonStyle(
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent,
					disabledBorderStroke: Color(UIColor.separator)
				)
			)
		}
		.padding(.horizontal)
		.offset(x: 0, y: -40) // move center upwards
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		payToOpen_feePercent = context.payToOpen.v1.feePercent * 100 // 0.01 => 1%
		payToOpen_minFeeSat = context.payToOpen.v1.minFeeSat
	}
	
	func formatFeePercent() -> String {
		
		let formatter = NumberFormatter()
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: payToOpen_feePercent))!
	}
}

struct IntroView3: View {
	
	let finish: () -> Void
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Keep Control")
				.font(.title)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 25) {
				
				Text("A backup phrase is generated, storing all the information needed to restore your Bitcoin funds.")
				
				Text("Keep it safe and secret!")
			}
			.multilineTextAlignment(.center)
			.padding(.top, 30)
			.padding(.horizontal, 20) // 20+20=40
			.padding(.bottom, 40)
			
			Button {
				finish()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text("Get started")
					Image(systemName: "bolt.fill")
						.imageScale(.medium)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
			}
			.buttonStyle(
				ScaleButtonStyle(
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent,
					disabledBorderStroke: Color(UIColor.separator)
				)
			)
		}
		.padding(.horizontal)
		.offset(x: 0, y: -40) // move center upwards
	}
}
