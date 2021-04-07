import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloseChannelsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct CloseChannelsView : MVIView {
	
	@StateObject var mvi = MVIState({ $0.closeChannelsConfiguration() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@ViewBuilder
	var view: some View {

		main
			.padding(.top, 30)
			.padding([.leading, .trailing], 30)
			.padding(.bottom, 10)
			.navigationBarTitle("Close channels", displayMode: .inline)
	}
	
	@ViewBuilder
	var main: some View {
		
		if let model = mvi.model as? CloseChannelsConfiguration.ModelReady {
			if model.channels.count == 0 {
				EmptyWalletView()
			} else {
				StandardWalletView(model: model, postIntent: mvi.intent)
			}
		} else if let model = mvi.model as? CloseChannelsConfiguration.ModelChannelsClosed {
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

		VStack(alignment: .leading) {
			let totalSats = model.channels.map { $0.balance }.reduce(0, +)
			let formattedSats = Utils.formatBitcoin(sat: totalSats, bitcoinUnit: .sat)
			
			if model.channels.count == 1 {
				Text(
					"You currenly have 1 Lightning channel" +
					" with a balance of \(formattedSats.string)."
				)
			} else {
				Text(
					"You currently have \(String(model.channels.count)) Lightning channels" +
					" with an aggragate balance of \(formattedSats.string)."
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
					.foregroundColor(Color.appNegative)
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
						borderStroke: Color.appAccent,
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
		log.trace("checkBitcoinAddress()")
		
		let business = AppDelegate.get().business
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
		log.trace("drainWallet()")
		
		popoverState.display.send(PopoverItem(
		
			ConfirmationPopover(confirmAction: confirmDrainWallet).anyView,
			dismissable: false
		))
	}
	
	func confirmDrainWallet() -> Void {
		log.trace("confirmDrainWallet()")
		
		postIntent(
			CloseChannelsConfiguration.IntentMutualCloseAllChannels(
				address: bitcoinAddress
			)
		)
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
					.foregroundColor(Color.appPositive)

				Text("Funds sent")
					.font(.title)
			}
			.padding(.bottom, 30)

			VStack(alignment: .leading) {

				if model.channels.count > 1 {
					Text("Expect to receive \(model.channels.count) separate payments.")
						.padding(.bottom, 10)
				}

				let intro = (model.channels.count == 1)
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

			} // </VStack>
		}
	}
}

fileprivate struct FooterView : View {
	
	var body: some View {
		
		// The "send to bitcoin address" functionality isn't available in lightning-kmp yet.
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
		log.trace("cancel()")
		popoverState.close.send()
	}
	
	func didTapConfirm() -> Void {
		log.trace("confirm()")
		popoverState.close.send()
		confirmAction()
	}
}

// MARK: -

class CloseChannelsView_Previews: PreviewProvider {

	static var previews: some View {
		
		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelLoading()
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")

		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelReady(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.normal
					)
				], address: "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelChannelsClosed(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					)
				])
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelChannelsClosed(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					),
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					)
				])
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
	}
}
