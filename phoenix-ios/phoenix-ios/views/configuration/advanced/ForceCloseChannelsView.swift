import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ForceCloseChannelsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let CONTENT_PADDING_TOP: CGFloat = 30
fileprivate let CONTENT_PADDING_LEFT_RIGHT: CGFloat = 30
fileprivate let CONTENT_PADDING_BOTTOM: CGFloat = 20

struct ForceCloseChannelsView : MVIView {
	
	@StateObject var mvi = MVIState({ $0.forceCloseChannelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@ViewBuilder
	var view: some View {
		
		main.navigationBarTitle(
			NSLocalizedString("Force close channels", comment: "Navigation bar title"),
			displayMode: .inline
		)
		
		// Note:
		//   Some views use a ScrollView,
		//   so the CONTENT_PADDING is applied to the scrollViewContent,
		//   and NOT to the ScrollView itself.
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
		.padding(.top, CONTENT_PADDING_TOP)
		.padding([.leading, .trailing], CONTENT_PADDING_LEFT_RIGHT)
		.padding(.bottom, CONTENT_PADDING_BOTTOM)
	}
}

fileprivate struct EmptyWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .leading) {
			
			Text("You currently don't have any channels that can be closed.")
				.padding(.bottom)
			
			Text(styled: NSLocalizedString(
				"""
				Payment channels are automatically created when you receive payments. \
				Use the **Receive** screen to receive via the Lightning network.
				""",
				comment: "ForceCloseChannelsView"
			))
			.padding(.bottom)
			
			Text(styled: NSLocalizedString(
				"""
				You can also use the **Payment Channels** screen to inspect the state of your channels.
				""",
				comment: "ForceCloseChannelsView"
			))

			Spacer()
		}
		.padding(.top, CONTENT_PADDING_TOP)
		.padding([.leading, .trailing], CONTENT_PADDING_LEFT_RIGHT)
		.padding(.bottom, CONTENT_PADDING_BOTTOM)
	}
}

fileprivate struct StandardWalletView : View {
	
	let model: CloseChannelsConfiguration.ModelReady
	let postIntent: (CloseChannelsConfiguration.Intent) -> Void
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		ScrollView {
			scrollViewContent
		}
	}
	
	var scrollViewContent: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			VStack(alignment: .leading) {
				
				Text("Force close allows you to unilaterally close your channels.")
					.padding(.bottom)
				
				Text(styled: NSLocalizedString(
					"""
					This feature is not a "fix everything magic button". \
					It is here as a safety measure and \
					**should only be used in extreme scenarios**. \
					For example, if your peer (ACINQ) disappears permanently, \
					preventing you from spending your money. In all other cases, \
					**if you experience issues with Phoenix you should contact support**.
					""",
					comment: "ForceCloseChannelsView"
				))
				.padding(.bottom)
				
				Text(styled: NSLocalizedString(
					"""
					Force closing channels will cost you money (to cover the on-chain fees) \
					and will cause your funds to be locked for days. \
					**Do not uninstall the app until your channels are fully closed or you will lose money.**
					""",
					comment: "ForceCloseChannelsView"
				))
				.padding(.bottom)
				
				Text("Do not use this feature if you don't fully understand what it does.")
					.bold()
				
			} // </VStack>
			
			Line()
				.stroke(Color.appAccent, style: StrokeStyle(lineWidth: 2, lineCap: .round))
				.frame(width: 40, height: 2)
				.padding([.top, .bottom], 25)
			
			VStack(alignment: .leading) {
			
				Text("Funds will eventually be sent to:")
					.padding(.bottom)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					Image(systemName: "link")
						.imageScale(.large)
						.padding(.trailing, 4)
					
					Text(model.address)
						.font(.system(.body, design: .monospaced))
						.lineLimit(nil)
						.minimumScaleFactor(0.5) // problems with truncation
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = model.address
							}) {
								Text("Copy")
							}
						}
				}
				.padding(.bottom)
				
				Text("This address is derived from your seed and belongs to you.")
					.font(.subheadline)
					.foregroundColor(.secondary)
				
			} // </VStack>
			.padding(.bottom)
			
			Button {
				forceCloseChannels()
			} label: {
				HStack {
					Image(systemName: "cross.circle")
						.imageScale(.small)

					Text("Force close channels")
				}
				.font(.body)
				.foregroundColor(Color(red: 0.99, green: 0.99, blue: 1.0))
			}
			.buttonStyle(PlainButtonStyle())
			.padding([.top, .bottom], 8)
			.padding(.leading, 8)
			.padding(.trailing, 12)
			.background(Color.appNegative)
			.cornerRadius(16)
			
		} // </VStack>
		.padding(.top, CONTENT_PADDING_TOP)
		.padding([.leading, .trailing], CONTENT_PADDING_LEFT_RIGHT)
		.padding(.bottom, CONTENT_PADDING_BOTTOM)
	}
	
	func forceCloseChannels() -> Void {
		log.debug("forceCloseChannels()")
		
		popoverState.display.send(PopoverItem(
		
			ConfirmationPopover(
				confirmAction: confirmForceCloseChannels
			).anyView,
			dismissable: false
		))
	}
	
	func confirmForceCloseChannels() -> Void {
		log.trace("confirmForceCloseChannels()")
		
		postIntent(
			CloseChannelsConfiguration.IntentForceCloseAllChannels()
		)
	}
}

fileprivate struct FundsSentView : View {
	
	let model: CloseChannelsConfiguration.ModelChannelsClosed
	
	var body: some View {
		
		ScrollView {
			scrollViewContent
		}
	}
	
	@ViewBuilder
	var scrollViewContent: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Image(systemName: "paperplane.fill")
				.renderingMode(.template)
				.resizable()
				.aspectRatio(contentMode: .fit)
				.frame(width: 64, height: 64)
				.foregroundColor(Color.appPositive)
				.padding(.bottom, 10)

			Text("Funds sent")
				.font(.title)
				.padding(.bottom, 30)

			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				if model.channels.count > 1 {
					Text("Expect to receive \(model.channels.count) separate payments.")
						.padding(.bottom, 10)
				}

				let msg = (model.channels.count <= 1)
					? NSLocalizedString(
						"""
						The closing transaction is in your transactions list on the __main__ screen. \
						And you can view the status of your channels in the __channels list__ screen.
						""",
						comment: "label text"
					)
					: NSLocalizedString(
						"""
						The closing transactions are in your transactions list on the __main__ screen. \
						And you can view the status of your channels in the __channels list__ screen.
						""",
						comment: "label text"
					)
				
				Text(styled: msg)
					.lineLimit(nil) // text is getting truncated for some reason
				
			} // </VStack>

		} // </VStack>
		.padding(.top, CONTENT_PADDING_TOP)
		.padding([.leading, .trailing], CONTENT_PADDING_LEFT_RIGHT)
		.padding(.bottom, CONTENT_PADDING_BOTTOM)
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
					Text("Confirm")
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

class ForceCloseChannelsView_Previews: PreviewProvider {
	
	static var previews: some View {
		
		NavigationView {
			ForceCloseChannelsView().mock(
				CloseChannelsConfiguration.ModelLoading()
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			ForceCloseChannelsView().mock(
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
			ForceCloseChannelsView().mock(
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
			ForceCloseChannelsView().mock(
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
		
		ConfirmationPopover(confirmAction: {})
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
	}
}
