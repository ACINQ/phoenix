import SwiftUI
import PhoenixShared
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ElectrumConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ElectrumConfigurationView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.electrumConfiguration() })
	
	@State var didAppear = false
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	func connectionInfo() -> (String, String) {
		
		var status: String
		var address: String
		
		if mvi.model.connection is Lightning_kmpConnection.ESTABLISHED {
			
			status = NSLocalizedString("Connected to:", comment: "Connection status")
			if let server = mvi.model.currentServer {
				address = "\(server.host):\(server.port)"
			} else {
				address = "?" // this state shouldn't be possible
			}
			
		} else if mvi.model.connection is Lightning_kmpConnection.ESTABLISHING {
			
			status = NSLocalizedString("Connecting to:", comment: "Connection status")
			if let server = mvi.model.currentServer {
				address = "\(server.host):\(server.port)"
			} else {
				address = "?" // this state shouldn't be possible
			}
			
		} else {
			
			status = NSLocalizedString("Will connect to:", comment: "Connection status")
			if let customConfig = mvi.model.configuration as? ElectrumConfig.Custom {
				address = "\(customConfig.server.host):\(customConfig.server.port)"
			} else {
				address = NSLocalizedString("Random server", comment: "Connection info")
			}
		}
		
		return (status, address)
	}
	
	@ViewBuilder
	var view: some View {

		main.navigationBarTitle(
			NSLocalizedString("Electrum server", comment: "Navigation bar title"),
			displayMode: .inline
		)
	}

	@ViewBuilder
	var main: some View {
		
		List {
				
			Section(header: ListHeader(), content: {}).textCase(nil)
			
			Section(header: Text("Configuration")) {
			
				VStack(alignment: .leading) {
					
					let (status, address) = connectionInfo()
					
					HStack {
						Text(status)
							.foregroundColor(Color.secondary)
						
						Spacer()
						Button {
							didTapModify()
						} label: {
							HStack {
								Image(systemName: "square.and.pencil").imageScale(.small)
								Text("Modify")
							}
						}
					}
					
					Text(address).bold()
						.padding(.top, 2)
					
					if let errMsg = mvi.model.error?.message {
						Text(errMsg)
							.foregroundColor(Color.appNegative)
							.padding(.top, 2)
					}
				}
				
			} // </Section: Configuration>
			
			Section(header: Text("Status")) {
				
				ListItem(header: Text("Block height")) {

					let height = mvi.model.blockHeight
					Text(verbatim: height > 0 ? height.formatInDecimalStyle() : "-")
				}
				
				ListItem(header: Text("Tip timestamp")) {
					
					let time = mvi.model.tipTimestamp
					Text(verbatim: time > 0 ? time.formatDateS() : "-")
				}
				
				ListItem(header: Text("Fee rate")) {
					
					if mvi.model.feeRate > 0 {
						Text("\(mvi.model.feeRate.formatInDecimalStyle()) sat/byte")
					} else {
						Text(verbatim: "-")
					}
				}
				
			} // </Section: Status>
			
		} // </List>
		.listStyle(GroupedListStyle())
		.onAppear() {
			onAppear()
		}
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if deepLinkManager.deepLink == .electrum {
				// Reached our destination
				deepLinkManager.broadcast(nil)
			}
		}
	}
	
	func didTapModify() {
		log.trace("didTapModify()")
		
		shortSheetState.display(dismissable: false) {
			
			ElectrumAddressSheet(mvi: mvi)
		}
	}
	
	struct ListHeader: View {
		
		var body: some View {
			
			Text(
				"""
				By default, Phoenix connects to random Electrum servers in order to access the \
				Bitcoin blockchain. You can also choose to connect to your own Electrum server.
				"""
			)
			.font(.body)
			.foregroundColor(Color.primary)
			.padding(.top, 10)
		}
	}
	
	struct ListItem<Content>: View where Content: View {
		
		let header: Text
		let content: () -> Content
		
		init(header: Text, @ViewBuilder content: @escaping () -> Content) {
			self.header = header
			self.content = content
		}
		
		var body: some View {
			
			HStack(alignment: .top) {
				header
					.font(.subheadline)
					.fontWeight(.thin)
					.frame(width: 90, alignment: .leading)

				content()
					.frame(maxWidth: .infinity, alignment: .leading)
			}
			.frame(maxWidth: .infinity, alignment: .leading)
		}
	}
}
