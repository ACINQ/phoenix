import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ChannelsConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ChannelsConfigurationView: View {

	var body: some View {
		
		MVIView({ $0.channelsConfiguration() }) { model, postIntent in
			
			mainView(model, postIntent)
		}
	}
	
	@ViewBuilder func mainView(
		_ model: ChannelsConfiguration.Model,
		_ postIntent: @escaping (ChannelsConfiguration.Intent) -> Void
	) -> some View {
	
		Group {
			if (model.channels.isEmpty) {
				NoChannelsView(model: model, postIntent: postIntent)
			} else {
				ChannelsView(model: model, postIntent: postIntent)
			}
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle("My payment channels", displayMode: .inline)
    }
}

fileprivate struct NoChannelsView : View {
	
	let model: ChannelsConfiguration.Model
	let postIntent: (ChannelsConfiguration.Intent) -> Void
	
	var body: some View {
		
		VStack {
			Text("You don't have any payment channels.")
				.padding()
			
			Spacer()
			FooterView(model: model)
		}
	}
}

fileprivate struct ChannelsView : View {
	
	let model: ChannelsConfiguration.Model
	let postIntent: (ChannelsConfiguration.Intent) -> Void
	
	@State var sharing: String? = nil
	@StateObject var toast = Toast()
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		ZStack {
			
			VStack {
				
				ScrollView {
					LazyVStack(pinnedViews: [.sectionHeaders]) {
						Section(header: ChannelHeaderView(model: model, sharing: $sharing)) {
							ForEach(model.channels, id: \.id) { channel in
								ChannelRowView(
									channel: channel,
									sharing: $sharing,
									toast: toast
								)
							}
						}
					}
					
				} // </ScrollView>
				
				FooterView(model: model)
				
			} // </VStack>
			
			toast.view()
			
		} // </ZStack>
	}
}

struct ChannelHeaderView: View {
	
	let model: ChannelsConfiguration.Model
	@Binding var sharing: String?
	
	@Environment(\.colorScheme) var colorScheme
	
	var body: some View {
		HStack {
			
			if model.channels.count == 1 {
				Text("1 channel")
			} else {
				Text("\(model.channels.count) channels")
			}
			
			Spacer()
			
			Button {
				sharing = model.json
			} label: {
				Image(systemName: "square.and.arrow.up")
				Text("Share channel list")
			}
			.sharing($sharing)
		}
		.frame(maxWidth: .infinity)
		.padding()
		.background(
			Color(
				colorScheme == ColorScheme.light
				? UIColor.systemGroupedBackground
				: UIColor.secondarySystemGroupedBackground
			)
		)
	}
}

fileprivate struct ChannelRowView: View {
	
	let channel: ChannelsConfiguration.ModelChannel
	@Binding var sharing: String?
	@ObservedObject var toast: Toast
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack {
			
			Button {
				showChannelInfoPopover()
			} label: {
			
				HStack {
					Image("ic_bullet")
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: 10, height: 10)
						.foregroundColor(channel.isOk ? .appGreen : .appRed)
				
					Text(channel.stateName)
						.foregroundColor(Color.primary)
					
					Spacer()
					
					if let c = channel.commitments {
						Text("\(c.first!) / \(c.second!) sat")
							.foregroundColor(Color.primary)
					}
				}
				.padding([.leading], 10)
				.padding([.top, .bottom], 8)
				.padding(.trailing)
			}
			
			Divider()
		}
	}
	
	func showChannelInfoPopover() {
		log.trace("showChannelInfoPopover()")
		
		popoverState.dismissable.send(true)
		popoverState.displayContent.send(
			
			ChannelInfoPopup(
				channel: channel,
				sharing: $sharing,
				toast: toast
			).anyView
		)
	}
}

fileprivate struct FooterView: View {
	
	let model: ChannelsConfiguration.Model
	
	@Environment(\.colorScheme) var colorScheme
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading) {
			Text("Your Node ID:")
				.font(.footnote)
				.padding(.bottom, 1)
			
			Text("\(model.nodeId)")
				.font(.footnote)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = model.nodeId
					}) {
						Text("Copy")
					}
				}
		}
		.frame(maxWidth: .infinity, alignment: .leading)
		.padding([.top, .bottom], 10)
		.padding([.leading, .trailing])
		.background(
			Color(
				colorScheme == ColorScheme.light
				? UIColor.systemGroupedBackground
				: UIColor.secondarySystemGroupedBackground
			)
			.edgesIgnoringSafeArea(.bottom)
		)
	}
}

fileprivate struct ChannelInfoPopup: View {
	
	let channel: ChannelsConfiguration.ModelChannel
	@Binding var sharing: String?
	@ObservedObject var toast: Toast
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack {
			ScrollView {
				Text(channel.json)
					.font(.caption)
					.padding()
			}
			.frame(height: 300)
			.frame(maxWidth: .infinity, alignment: .leading)
			
			HStack {
				Button {
					UIPasteboard.general.string = channel.json
					toast.toast(text: "Copied to pasteboard")
				} label: {
					Image(systemName: "square.on.square")
						.resizable()
						.scaledToFit()
						.frame(width: 22, height: 22)
				}

				Divider()
					.frame(height: 30)
					.padding([.leading, .trailing], 8)

				Button {
					sharing = channel.json
				} label: {
					Image(systemName: "square.and.arrow.up")
						.resizable()
						.scaledToFit()
						.frame(width: 22, height: 22)
				}

				if let txUrl = channel.txUrl {
					Divider()
						.frame(height: 30)
						.padding([.leading, .trailing], 8)

					Button {
						if let url = URL(string: txUrl) {
							UIApplication.shared.open(url)
						}
					} label: {
						Text("Tx").font(.title2)
					}
				}

				Spacer()
				Button("OK") {
					closePopover()
				}
				.font(.title2)
			}
			.padding(.top, 10)
			.padding([.leading, .trailing])
			.padding(.bottom, 10)
			.background(
				Color(UIColor.secondarySystemBackground)
			)
			
		} // </VStack>
	}
	
	func closePopover() -> Void {
		log.trace("[ChannelInfoPopup] closePopover()")
		
		popoverState.close.send()
	}
}


// MARK:-

class ChannelsConfigurationView_Previews : PreviewProvider {
	
	static let channel1 = ChannelsConfiguration.ModelChannel(
		id: "b50bf19d16156de8231f6d3d3fb3dd105ba338de5366d0421b0954b9ceb0d4f8",
		isOk: true,
		stateName: "Normal",
		commitments: KotlinPair(first: 50000, second: 200000),
		json: "{Everything is normal!}",
		txUrl: "http://google.com"
	)
	
	static let channel2 = ChannelsConfiguration.ModelChannel(
		id: "e5366d0421b0954b9ceb0d4f8b50bf19d16156de8231f6d3d3fb3dd105ba338d",
		isOk: false,
		stateName: "Woops",
		commitments: nil,
		json: "{Woops!}",
		txUrl: nil
	)
	
	static let model1 = ChannelsConfiguration.Model(
		nodeId: "03af0ed6052cf28d670665549bc86f4b721c9fdb309d40c58f5811f63966e005d0",
		json: "{}",
		channels: []
	)
	
	static let model2 = ChannelsConfiguration.Model(
		nodeId: "03af0ed6052cf28d670665549bc86f4b721c9fdb309d40c58f5811f63966e005d0",
		json: "{}",
		channels: [channel1, channel2]
	)
	
	static let mockModel = model2

	static var previews: some View {
		
//		NavigationView {
//			mockView(ChannelsConfigurationView())
//		}
//		.preferredColorScheme(.light)
//		.previewDevice("iPhone 8")

		NavigationView {
			mockView(ChannelsConfigurationView())
		}
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
		
//		NavigationView {
//			mockView(ChannelsConfigurationView())
//		}
//		.preferredColorScheme(.light)
//		.previewDevice("iPhone 11")
    }

	#if DEBUG
	@objc class func injected() {
		UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
	}
	#endif
}
