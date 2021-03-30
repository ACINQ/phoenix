import Foundation
import SwiftUI
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
			
			if AppDelegate.get().business.chain.isTestnet() {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
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
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground) // smoother animation when dismissing
		.edgesIgnoringSafeArea(.bottom)
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
					borderStroke: Color.appHorizon,
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
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Automatic Channel Creation")
				.font(.title)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 25) {
				
				Text("Payment channels are automatically created when needed.")
				
				let min = Utils.formatBitcoin(sat: 10_000, bitcoinUnit: .sat)
				Group {
					Text("The fee is ") +
					Text("0.10%").bold() +
					Text(" with a minimum fee of ") +
					Text(min.string).bold() + Text(".")
				}
				
				Text("For example, to receive $750, the channel creation fee is $0.75.")
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
					borderStroke: Color.appHorizon,
					disabledBorderStroke: Color(UIColor.separator)
				)
			)
		}
		.padding(.horizontal)
		.offset(x: 0, y: -40) // move center upwards
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
					borderStroke: Color.appHorizon,
					disabledBorderStroke: Color(UIColor.separator)
				)
			)
		}
		.padding(.horizontal)
		.offset(x: 0, y: -40) // move center upwards
	}
}
