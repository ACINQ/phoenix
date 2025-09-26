import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "ElectrumConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ElectrumConfigurationView: MVIView {
	
	@StateObject var mvi = MVIState({ Biz.business.controllers.electrumConfiguration() })
	
	@StateObject var customElectrumServerObserver = CustomElectrumServerObserver()
	
	@State var didAppear = false
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		content()
			.navigationTitle(NSLocalizedString("Electrum server", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}

	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_configuration()
			section_status()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear() {
			onAppear()
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			Text(
				"""
				To secure your payment channels Phoenix monitors the Bitcoin blockchain \
				through Electrum servers.
				
				By default, random servers are used. You can also configure Phoenix to \
				connect only to your own server.
				"""
			)
		} // </Section>
	}
	
	@ViewBuilder
	func section_configuration() -> some View {
		
		Section {
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
				
				if let problem = customElectrumServerObserver.problem {
					Group {
						switch problem {
						case .requiresOnionAddress:
							Text("Tor is enabled. This server should use an onion address.")
						case .badCertificate:
							Text("Bad certificate !")
						}
					}
					.foregroundColor(Color.appNegative)
					.padding(.top, 2)
				}
			} // </VStack>
			
		} header: {
			Text("Configuration")
		} // </Section>
	}
	
	@ViewBuilder
	func section_status() -> some View {
		
		Section(header: Text("Status")) {
			
			ListItem(header: Text("Block height")) {

				let height = mvi.model.blockHeight
				Text(verbatim: height > 0 ? formatInDecimalStyle(height) : "-")
			}
			
			ListItem(header: Text("Tip timestamp")) {
				
				let time = mvi.model.tipTimestamp
				if time > 0 {
					Text(verbatim: time.toDate(from: .seconds).format(date: .long, time: .short))
				} else {
					Text(verbatim: "-")
				}
			}
			
			ListItem(header: Text("Fee rate")) {
				
				if mvi.model.feeRate > 0 {
					Text("\(formatInDecimalStyle(mvi.model.feeRate)) sat/byte")
				} else {
					Text(verbatim: "-")
				}
			}
			
		} // </Section>
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func connectionInfo() -> (String, String) {
		
		var status: String
		var address: String
		
		if mvi.model.connection.isEstablished() {
			
			status = NSLocalizedString("Connected to:", comment: "Connection status")
			if let server = mvi.model.currentServer {
				address = "\(server.host):\(server.port)"
			} else {
				address = "?" // this state shouldn't be possible
			}
			
		} else if mvi.model.connection.isEstablishing() {
			
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
	
	func formatInDecimalStyle(_ value: Int32) -> String {
		return formatInDecimalStyle(NSNumber(value: value))
	}
	
	func formatInDecimalStyle(_ value: Int64) -> String {
		return formatInDecimalStyle(NSNumber(value: value))
	}
	
	func formatInDecimalStyle(_ value: NSNumber) -> String {
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true
		return formatter.string(from: value)!
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if let deepLink = deepLinkManager.deepLink, deepLink == .electrum {
				// Reached our destination
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkManager.unbroadcast(deepLink)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapModify() {
		log.trace("didTapModify()")
		
		smartModalState.display(dismissable: false) {
			
			ElectrumAddressSheet(mvi: mvi)
		}
	}
}

fileprivate struct ListItem<Content>: View where Content: View {
	
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
