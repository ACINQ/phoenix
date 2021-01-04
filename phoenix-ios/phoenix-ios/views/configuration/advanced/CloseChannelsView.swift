import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var logger = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloseChannelsView"
)
#else
fileprivate var logger = Logger(OSLog.disabled)
#endif

struct CloseChannelsView : View {
	
	var body: some View {
		
		MVIView({ $0.closeChannelsConfiguration() }) { model, postIntent in
			
			main(model, postIntent)
		}
		.padding(.top, 40)
		.padding([.leading, .trailing, .bottom])
		.navigationBarTitle("Close channels", displayMode: .inline)
	}
	
	@ViewBuilder func main(
		_ model: CloseChannelsConfiguration.Model,
		_ postIntent: @escaping (CloseChannelsConfiguration.Intent) -> Void
	) -> some View {
		
		if let model = model as? CloseChannelsConfiguration.ModelReady {
			if model.channelCount == 0 {
				EmptyWalletView()
			} else {
				StandardWalletView(model: model, postIntent: postIntent)
			}
		} else if let model = model as? CloseChannelsConfiguration.ModelChannelsClosed {
			FundsSentView(model: model)
		} else {
			LoadingWalletView()
		}
	}
}

fileprivate struct LoadingWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .center) {
		
			ProgressView()
				.progressViewStyle(CircularProgressViewStyle())
				.padding(.bottom, 5)
			
			Text("Checking channel state...")
			
			Spacer()
		}
	}
}

fileprivate struct EmptyWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .leading) {
			
			Text("You currently don't have any channels that can be closed.")
				.padding(.bottom, 20)
			
			Group {
				Text("Payment channels are automatically created when you receive payments. ") +
				Text("Use the ") +
				Text("Receive").bold() +
				Text(" screen to receive via the Lightning network.")
			}
			.padding(.bottom, 20)

			Text("You can also use the ") +
			Text("Payment Channels").bold() +
			Text(" screen to inspect the state of your channels.")

			Spacer()

			FooterView()
		}
	}
}

fileprivate struct StandardWalletView : View {
	
	let model: CloseChannelsConfiguration.ModelReady
	let postIntent: (CloseChannelsConfiguration.Intent) -> Void
	
	@State var bitcoinAddress: String = ""
	@State var isValidAddress: Bool = false
	@State var detailedErrorMsg: String? = nil
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		ZStack {
			main()
		}
	}
	
	@ViewBuilder func main() -> some View {
		
		VStack(alignment: .leading) {
			
			let formattedSats = Utils.formatBitcoin(sat: model.sats, bitcoinUnit: .satoshi)
			
			if model.channelCount == 1 {
				Text(
					"You currenly have 1 Lightning channel" +
					" with a balance of \(formattedSats.string)."
				)
			} else {
				Text(
					"You currently have \(String(model.channelCount)) Lightning channels" +
					" with an aggragated balance of \(formattedSats.string)."
				)
			}
			
			Group {
				Text(
					"Funds can be sent to a Bitcoin wallet." +
					" Make sure the address is correct before sending."
				)
			}
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			HStack {
				TextField("Bitcoin address", text: $bitcoinAddress)
					.onChange(of: bitcoinAddress) { _ in
						checkBitcoinAddress()
					}
				
				Button {
					bitcoinAddress = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(bitcoinAddress == "")
			}
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.background(Capsule().stroke(Color(UIColor.separator)))
			.padding(.bottom, 10)
			
			if let detailedErrorMsg = detailedErrorMsg {
				Text(detailedErrorMsg)
					.foregroundColor(Color.appRed)
			} else {
				Button {
					drainWallet()
				} label: {
					HStack {
						Image(systemName: "bitcoinsign.circle")
							.imageScale(.small)
						
						Text("Drain my wallet")
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
				.disabled(!isValidAddress)
			}
			
			Spacer()
			
			FooterView()
		}
	}
	
	func checkBitcoinAddress() -> Void {
		logger.trace("checkBitcoinAddress()")
		
		let business = PhoenixApplicationDelegate.get().business
		let result = business.util.parseBitcoinAddress(addr: bitcoinAddress)
		
		if let error = result.left {
			
			if let error = error as? Utilities.BitcoinAddressErrorChainMismatch {
				detailedErrorMsg = NSLocalizedString(
					"The address is for \(error.addrChain.name)," +
					" but you're on \(error.myChain.name)",
					comment: "Error message - parsing bitcoin address"
				)
			}
			else if error is Utilities.BitcoinAddressErrorUnknownBech32Version {
				detailedErrorMsg = NSLocalizedString(
					"Unknown Bech32 version",
					comment: "Error message - parsing bitcoin address"
				)
			}
			else {
				detailedErrorMsg = nil
			}
			
			isValidAddress = false
			
		} else {
			isValidAddress = true
			detailedErrorMsg = nil
		}
	}
	
	func drainWallet() -> Void {
		logger.trace("drainWallet()")
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			ConfirmationPopover(confirmAction: confirmDrainWallet).anyView
		)
	}
	
	func confirmDrainWallet() -> Void {
		logger.trace("confirmDrainWallet()")
	
	//	popoverState.dismissable.send(true)
	//	popoverState.displayContent.send(
	//		NotImplementedPopover().anyView
	//	)
		
		postIntent(CloseChannelsConfiguration.IntentCloseAllChannels(address: bitcoinAddress))
	}
}

fileprivate struct FundsSentView : View {
	
	let model: CloseChannelsConfiguration.ModelChannelsClosed
	
	var body: some View {
		
		ScrollView {
			VStack {
				Image(systemName: "paperplane.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 64, height: 64)
					.foregroundColor(Color.appGreen)
				
				Text("Funds sent")
					.font(.title)
			}
			.padding(.bottom, 30)
			
			VStack(alignment: .leading) {
				
				if model.channelCount > 1 {
					Text("Expect to receive \(model.channelCount) separate payments.")
						.padding(.bottom, 10)
				}
				
				let intro = (model.channelCount == 1)
					? NSLocalizedString(
						"The closing transaction is in your transactions list on the ",
						comment: "label text"
					)
					: NSLocalizedString(
						"The closing transactions are in your transactions list on the ",
						comment: "label text"
					)
				
				Text(intro) +
				Text("main").italic() +
				Text(" screen. And you can view the status of your channels in the ") +
				Text("channels list").italic() +
				Text(" screen.")
			}
		}
	}
}

fileprivate struct FooterView : View {
	
	var body: some View {
		
		// The "send to bitcoin address" functionality isn't available in eclair-kmp yet.
		// When added, and integrated into Send screen, the code below should be uncommented.
		// 
		Group {
			Text("Use this feature to transfer ") +
			Text("all").italic() +
			Text(" your funds to a Bitcoin address. ") // +
		//	Text("If you only want to send ") +
		//	Text("some").italic() +
		//	Text(" of your funds, then you can use the ") +
		//	Text("Send").bold().italic() +
		//	Text(" screen. Just scan/enter a Bitcoin address and Phoenix does the rest.")
		}
		.font(.footnote)
		.foregroundColor(.secondary)
	}
}

fileprivate struct ConfirmationPopover : View {
	
	let confirmAction: () -> Void
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
		
			VStack(alignment: .leading) {
				Text("Are you sure you want to proceed?")
			}
			.padding(.bottom, 20)
			
			HStack {
				Button {
					didTapCancel()
				} label : {
					Text("Cancel")
				}
				.padding(.trailing, 10)
				
				Button {
					didTapConfirm()
				} label : {
					Text("Send Funds")
				}
			}
			
		} // </VStack>
		.padding()
	}
	
	func didTapCancel() -> Void {
		logger.trace("cancel()")
		popoverState.close.send()
	}
	
	func didTapConfirm() -> Void {
		logger.trace("confirm()")
		popoverState.close.send()
		confirmAction()
	}
}

fileprivate struct NotImplementedPopover: View {
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
		
			VStack(alignment: .leading) {
				Text("Coming soon")
					.font(.title2)
					.padding(.bottom)
				
				Text("Sorry, we're still working on this. Check back later.")
			}
			.padding(.bottom, 20)
			
			HStack {
				Button {
					dismiss()
				} label : {
					Text("OK").font(.title2)
				}
			}
		
		}// </VStack>
		.padding()
	}
	
	func dismiss() -> Void {
		popoverState.close.send()
	}
}

// MARK: -

class CloseChannelsView_Previews: PreviewProvider {
	
//	static let model_1 = CloseChannelsConfiguration.ModelLoading()
//	static let model_2 = CloseChannelsConfiguration.ModelReady(channelCount: 0, sats: 0)
//	static let model_3 = CloseChannelsConfiguration.ModelReady(channelCount: 1, sats: 500_000)
//	static let model_4 = CloseChannelsConfiguration.ModelReady(channelCount: 3, sats: 1_500_000)
	static let model_5 = CloseChannelsConfiguration.ModelChannelsClosed(channelCount: 1, sats: 500_000)
	static let model_6 = CloseChannelsConfiguration.ModelChannelsClosed(channelCount: 3, sats: 1_500_500)
	
	static let mockModel = model_5
	
	static var previews: some View {
		
		NavigationView {
			mockView(CloseChannelsView())
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			mockView(CloseChannelsView())
		}
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
		
		NotImplementedPopover()
			.padding()
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
	}
}
