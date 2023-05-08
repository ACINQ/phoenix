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
	@EnvironmentObject private var deviceInfo: DeviceInfo
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
			}
			
			content()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationTitle("")
		.navigationBarTitleDisplayMode(.inline)
		.navigationBarHidden(true)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		TabView(selection: $selectedPage) {
			
			IntroView1(advance: advance)
				.tag(0)
			
			IntroView2(advance: advance)
				.tag(1)
			
			IntroView3(finish: wrappedFinish)
				.tag(2)
		}
		.tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
		.padding(.bottom, deviceInfo.isShortHeight ? 0 : 40)
	}
	
	func advance() -> Void {
		log.trace("advance()")
		
		withAnimation {
			selectedPage += 1
			UIAccessibility.post(notification: .screenChanged, argument: nil)
		}
	}
	
	func wrappedFinish() -> Void {
		
		finish()
		UIAccessibility.post(notification: .screenChanged, argument: nil)
	}
}

struct IntroView1: View, ViewName {
	
	let advance: () -> Void
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Spacer()
			
			Image("intro_btc")
				.resizable()
				.scaledToFit()
				.frame(maxWidth: 250, maxHeight: 250)
			
			Spacer()
			
			Text("Welcome!")
				.font(.title)
				.multilineTextAlignment(.center)
				.accessibilityAddTraits(.isHeader)
			
			Text("With Phoenix, sending and receiving bitcoins is easy and safe.")
				.multilineTextAlignment(.center)
				.padding(.vertical, 30)
			
			Button {
				advance()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text("Next")
					Image(systemName: "arrow.forward")
						.imageScale(.medium)
				}
				.font(.title3)
				.frame(maxWidth: .infinity)
				.padding(.vertical, 8)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.padding(.bottom)
		}
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
		.padding(.horizontal)
	}
}

struct IntroView2: View, ViewName {
	
	let advance: () -> Void
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Spacer()
			
			Image("intro_ln")
				.resizable()
				.scaledToFit()
				.frame(maxWidth: 250, maxHeight: 250)
			
			Spacer()
			
			Text("Bitcoin supercharged")
				.font(.title)
				.multilineTextAlignment(.center)
				.accessibilityAddTraits(.isHeader)
			
			Text("Phoenix uses payment channels to make Bitcoin fast and private.")
				.multilineTextAlignment(.center)
				.padding(.vertical, 30)
			
			Button {
				advance()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text("Next")
					Image(systemName: "arrow.forward")
						.imageScale(.medium)
				}
				.font(.title3)
				.frame(maxWidth: .infinity)
				.padding(.vertical, 8)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.padding(.bottom)
		}
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
		.padding(.horizontal)
	}
}

struct IntroView3: View, ViewName {
	
	let finish: () -> Void
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Spacer()
			
			Image("intro_cust")
				.resizable()
				.scaledToFit()
				.frame(maxWidth: 250, maxHeight: 250)
			
			Spacer()
			
			Text("Your key, your bitcoins")
				.font(.title)
				.multilineTextAlignment(.center)
				.accessibilityAddTraits(.isHeader)
			
			Text("Phoenix is self-custodial. You take control.")
				.multilineTextAlignment(.center)
				.padding(.top, 30)
				.padding(.bottom, 15)
			
			Text("You can restore your wallet at anytime using your secret key. Keep it safe!")
				.multilineTextAlignment(.center)
				.padding(.top, 15)
				.padding(.bottom, 30)
			
			Button {
				finish()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text("Get started")
					Image(systemName: "bolt.fill")
						.imageScale(.medium)
				}
				.font(.title3)
				.frame(maxWidth: .infinity)
				.padding(.vertical, 8)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.padding(.bottom)
		}
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
		.padding(.horizontal)
	}
}
