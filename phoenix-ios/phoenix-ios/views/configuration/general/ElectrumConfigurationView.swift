import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ElectrumConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ElectrumConfigurationView: View {
	
    var body: some View {
		
		MVIView({ $0.electrumConfiguration() }) { model, postIntent in
			
		//	OldLayout(model, postIntent)
			NewLayout(model, postIntent)
				.navigationBarTitle("Electrum server", displayMode: .inline)
		}
	}
}

struct NewLayout: View {
	
	let model: ElectrumConfiguration.Model
	let postIntent: (ElectrumConfiguration.Intent) -> Void
	
	@State var isModifying = false
	
	init(
		_ model: ElectrumConfiguration.Model,
		_ postIntent: @escaping (ElectrumConfiguration.Intent) -> Void
	) {
		self.model = model
		self.postIntent = postIntent
	}
	
	func connectionStatus() -> String {
		if model.connection == .established {
			return NSLocalizedString("Connected to:", comment: "Connection status")
		} else if model.connection == .establishing {
			return NSLocalizedString("Connecting to:", comment: "Connection status")
		} else {
			return NSLocalizedString("Will connect to:", comment: "Connection status")
		}
	}
	
	func connectionAddress() -> String {
		
		if model.connection == .established || model.electrumServer.customized {
			return model.electrumServer.address()
		} else {
			return NSLocalizedString("Random server", comment: "Connection info")
		}
	}
	
	var body: some View {
		
		List {
				
			Section(header: Text("Info")) {
				Text(
					"By default Phoenix connects to random Electrum servers in order to access the" +
					" Bitcoin blockchain. You can also choose to connect to your own Electrum server."
				)
			}
			
			Section(header: Text("Configuration")) {
			
				VStack(alignment: .leading) {
					
					HStack {
						Text(connectionStatus())
							.foregroundColor(Color.secondary)
						
						Spacer()
						Button {
							isModifying = true
						//	showPopover()
						} label: {
							HStack {
								Image(systemName: "square.and.pencil").imageScale(.small)
								Text("Modify")
							}
						}
					}
					
					Text(connectionAddress()).bold()
						.padding(.top, 2)
					
					if let errMsg = model.error?.message {
						Text(errMsg)
							.foregroundColor(Color.appRed)
							.padding(.top, 2)
					}
				}
				
			} // </Section: Configuration>
			
			Section(header: Text("Status")) {
				
				ListItem(header: Text("Block height")) {

					let height = model.electrumServer.blockHeight
					Text("\(height > 0 ? height.formatInDecimalStyle() : "-")")
				}
				
				ListItem(header: Text("Tip timestamp")) {
					
					let time = model.electrumServer.tipTimestamp
					Text("\(time > 0 ? time.formatDateS() : "-")")
				}
				
				ListItem(header: Text("Fee rate")) {
					
					if model.feeRate > 0 {
						Text("\(model.feeRate.formatInDecimalStyle()) sat/byte")
					} else {
						Text("-")
					}
				}
				
				if let xpub = model.xpub {
					ListItem(header: Text("Master public key")) {
						Text(xpub)
							.contextMenu {
								Button(action: {
									UIPasteboard.general.string = xpub
								}) {
									Text("Copy")
								}
							}
					}
				}
				
				if let path = model.path {
					ListItem(header: Text("Path")) {
						Text(path)
							.contextMenu {
								Button(action: {
									UIPasteboard.general.string = path
								}) {
									Text("Copy")
								}
							}
					}
				}
				
			} // </Section: Status>
			
		} // </List>
		.listStyle(GroupedListStyle())
		.sheet(isPresented: $isModifying) {
		
			ElectrumAddressPopup(
				model: model,
				postIntent: postIntent,
				showing: $isModifying
			).padding()
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

struct ElectrumAddressPopup: View {
	
	let model: ElectrumConfiguration.Model
	let postIntent: (ElectrumConfiguration.Intent) -> Void
	
	@Binding var showing: Bool
	
	@State var isCustomized: Bool
	@State var host: String
	@State var port: String
	
	@State var userHasMadeChanges: Bool = false
	@State var invalidHost = false
	@State var invalidPort = false
	
	init(
		model: ElectrumConfiguration.Model,
		postIntent: @escaping (ElectrumConfiguration.Intent) -> Void,
		showing: Binding<Bool>
	) {
		self.model = model
		self.postIntent = postIntent
		_showing = showing
		
		_isCustomized = State(initialValue: model.electrumServer.customized)
		
		if model.electrumServer.customized && model.electrumServer.host.count > 0 {
			_host = State(initialValue: model.electrumServer.host)
			_port = State(initialValue: String(model.electrumServer.port))
		} else {
			_host = State(initialValue: "")
			_port = State(initialValue: "")
		}
	}

	var body: some View {
		
		let titleWidth: CGFloat = 60
		
		VStack(alignment: .trailing, spacing: 0) {
			
			VStack(alignment: .leading) {
				
				Toggle(isOn: $isCustomized.animation()) {
					Text("Use a custom server")
				}
				.onChange(of: isCustomized) { _ in
					onToggleDidChange()
				}
				.padding(.trailing, 2) // Toggle draws outside its bounds
			}
			.padding(.bottom, 15)
			.background(Color(UIColor.systemBackground))
			.zIndex(1)
			
			if isCustomized { // animation control
			
				VStack(alignment: .leading) {
		
					// == Server: ([TextField][X]) ===
					HStack(alignment: .firstTextBaseline) {
						Text("Server")
							.font(.subheadline)
							.fontWeight(.thin)
							.foregroundColor(invalidHost ? Color.appRed : Color.primary)
							.frame(width: titleWidth)
	
						HStack {
							TextField("example.com", text: $host,
								onCommit: {
									onHostDidCommit()
								}
							)
							.keyboardType(.URL)
							.disableAutocorrection(true)
							.autocapitalization(.none)
							.onChange(of: host) { _ in
								onHostDidChange()
							}
	
							Button {
								host = ""
							} label: {
								Image(systemName: "multiply.circle.fill")
									.foregroundColor(.secondary)
							}
							.isHidden(!isCustomized || host == "")
						}
						.disabled(!isCustomized)
						.padding([.top, .bottom], 8)
						.padding(.leading, 16)
						.padding(.trailing, 8)
						.background(
							Capsule()
								.strokeBorder(Color(UIColor.separator))
						)
					}
					.frame(maxWidth: .infinity)
					.padding(.bottom)
					
					// == Port: ([TextField][X]) ===
					HStack(alignment: .firstTextBaseline) {
						Text("Port")
							.font(.subheadline)
							.fontWeight(.thin)
							.foregroundColor(invalidPort ? Color.appRed : Color.primary)
							.frame(width: titleWidth)
						
						HStack {
							HStack {
								TextField("50002", text: $port,
									onCommit: {
										onPortDidCommit()
									}
								)
								.keyboardType(.numberPad)
								.disableAutocorrection(true)
								.onChange(of: port) { _ in
									onPortDidChange()
								}
		
								Button {
									port = ""
								} label: {
									Image(systemName: "multiply.circle.fill")
										.foregroundColor(.secondary)
								}
								.isHidden(!isCustomized || port == "")
							}
							.disabled(!isCustomized)
							.padding([.top, .bottom], 8)
							.padding(.leading, 16)
							.padding(.trailing, 8)
							.background(
								Capsule()
									.strokeBorder(Color(UIColor.separator))
							)
						}
					}
					.frame(maxWidth: .infinity)
					.padding(.bottom, 30)
					
					if invalidHost {
						Text("Invalid server. Use format: domain.tld")
							.italic()
							.foregroundColor(Color.appRed)
							.font(.footnote)
					} else if invalidPort {
						Text("Invalid port. Valid range: [1 - 65535]")
							.italic()
							.foregroundColor(Color.appRed)
							.font(.footnote)
					} else {
						Text("Note: Server must have a valid certificate")
							.italic()
							.font(.footnote)
					}
			
				} // </VStack>
				.transition(.move(edge: .top))
				.zIndex(0)
				
			} // </if isCustomized>
			
			Spacer()
			
			HStack {
				
				Button("Cancel") {
					onCancelButtonTapped()
				}
				.font(.title2)
				.padding(.trailing, 30)
				
				Button("Save") {
					onSaveButtonTapped()
				}
				.font(.title2)
			}
			
		} // </VStack>
		.clipped()
		
	} // </var body: some View>
	
	func onToggleDidChange() -> Void {
		log.trace("onToggleDidChange()")
		userHasMadeChanges = true
	}
	
	func onHostDidChange() -> Void {
		log.trace("onHostDidChange()")
		userHasMadeChanges = true
	}
	
	func onHostDidCommit() -> Void {
		log.trace("onHostDidCommit()")
		checkHost()
	}
	
	func onPortDidChange() -> Void {
		log.trace("onPortDidChange()")
		userHasMadeChanges = true
	}
	
	func onPortDidCommit() -> Void {
		log.trace("onPortDidCommit()")
		checkPort()
	}
	
	func onCancelButtonTapped() -> Void {
		log.trace("onCancelButtonTapped()")
		showing = false
	}
	
	func onSaveButtonTapped() -> Void {
		log.trace("onSaveButtonTapped()")
		
		let checkedHost = checkHost()
		let checkedPort = checkPort()
		
		if let checkedHost = checkedHost,
		   let checkedPort = checkedPort
		{
			let address = "\(checkedHost):\(checkedPort)"
			postIntent(ElectrumConfiguration.IntentUpdateElectrumServer(customized: isCustomized, address: address))
			
			showing = false
		}
	}
	
	@discardableResult
	func checkHost() -> String? {
		log.trace("checkHost()")
		
		if isCustomized {
			
			let rawHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
			let urlStr = "http://\(rawHost)"
			
			// careful: URL(string: "http://") return non-nil
			
			if (rawHost.count > 0) && (URL(string: urlStr) != nil) {
				invalidHost = false
				return rawHost
			} else {
				invalidHost = true
				return nil
			}
			
		} else {
			
			invalidHost = false
			return ""
		}
	}
	
	@discardableResult
	func checkPort() -> UInt16? {
		log.trace("checkPort()")
		
		if isCustomized {
			
			let rawPort = port.trimmingCharacters(in: .whitespacesAndNewlines)
			
			if let parsedPort = UInt16(rawPort), parsedPort != 0 {
				invalidPort = false
				return parsedPort
			} else {
				invalidPort = true
				return nil
			}
			
		} else {
			
			invalidPort = false
			return 0
		}
	}
}

// MARK:-

class ElectrumConfigurationView_Previews: PreviewProvider {
	
	static let electrumServer1 = ElectrumServer(
		id: 0,
		host: "tn.not.fyi",
		port: 55002,
		customized: true,
		blockHeight: 123456789,
		tipTimestamp: 1599564210
	)
	
	static let electrumServer2 = ElectrumServer(
		id: 0,
		host: "",
		port: 0,
		customized: false,
		blockHeight: 123456789,
		tipTimestamp: 1599564210
	)
	
	static let mockModel = ElectrumConfiguration.Model(
		walletIsInitialized: true,
		connection: .closed,
		electrumServer: electrumServer2,
		feeRate: 9999,
		xpub: "vpub5ZqweUWHkV3Q5HrftL6c7L7rwvQdSPrQzPdFEd8tHU32EmduQqy1CgmAb4g1jQGoBFaGW4EcNhc1Bm9grBkV2hSUz787L7ALTfNbz3xNigS",
		path: "m/84'/1'/0'",
		error: nil
	)
	
	@State static var isShowing: Bool = true

	static var previews: some View {
//		mockView(ElectrumConfigurationView())
//			.preferredColorScheme(.light)
//			.previewDevice("iPhone 8")
//
//		mockView(ElectrumConfigurationView())
//			.preferredColorScheme(.dark)
//			.previewDevice("iPhone 8")
		
		ElectrumAddressPopup(
			model: mockModel,
			postIntent: { _ in },
			showing: $isShowing
		)
		.padding()
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
//
//		ElectrumAddressPopup(model: mockModel)
//			.padding()
//			.preferredColorScheme(.dark)
//			.previewDevice("iPhone 8")
	}

#if DEBUG
	@objc class func injected() {
		UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
	}
#endif
}
