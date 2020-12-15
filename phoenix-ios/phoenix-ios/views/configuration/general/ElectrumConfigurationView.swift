import SwiftUI
import PhoenixShared

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
/*
struct OldLayout: View {
	
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
	
	var body: some View {
		
		VStack(spacing: 0) {
			Text(
				"By default Phoenix connects to random Electrum servers in order to access the" +
				" Bitcoin blockchain. You can also choose to connect to your own Electrum server."
			)
			.padding()
			.frame(maxWidth: .infinity, alignment: .leading)

			Divider()

			CustomSection(header: "Server") {
				VStack(alignment: .leading) {
					if model.connection == .established {
						Text("Connected to:")
						Text(model.electrumServer.address()).bold()
					} else if model.connection == .establishing {
						Text("Connecting to:")
						Text(model.electrumServer.address()).bold()
					} else if model.electrumServer.customized {
						Text(model.walletIsInitialized ? "Not connected" : "You will connect to:")
						Text(model.electrumServer.address()).bold()
					} else {
						Text("Not connected")
					}
						
					if model.error != nil {
						Text(model.error?.message ?? "")
							.foregroundColor(Color.red)
					}

					Button {
					//	showPopover()
					} label: {
						HStack {
							Image(systemName: "square.and.pencil")
								.imageScale(.medium)
							Text("Set server")
						}
					}
					.buttonStyle(PlainButtonStyle())
					.padding(8)
					.background(Color.white)
					.cornerRadius(16)
					.overlay(
						RoundedRectangle(cornerRadius: 16)
							.stroke(Color.appHorizon, lineWidth: 2)
					)
				}
			}

			if model.walletIsInitialized {
				CustomSection(header: "Block height") {
					let blockHeight = model.electrumServer.blockHeight
					Text("\(blockHeight > 0 ? blockHeight.formatInDecimalStyle() : "-")")
				}

				CustomSection(header: "Tip timestamp") {
					let time = model.electrumServer.tipTimestamp
					Text("\(time > 0 ? time.formatDateS() : "-")")
				}

				CustomSection(header: "Fee rate") {
					if model.feeRate > 0 {
						Text("\(model.feeRate.formatInDecimalStyle()) sat/byte")
					} else {
						Text("-")
					}
				}

				CustomSection(header: "Master public key") {
					if model.xpub == nil || model.path == nil {
						Text("-")
					} else {
						VStack(alignment: .leading) {
							Text(model.xpub!)
							Text("Path: \(model.path!)").padding(.top)
						}
					}
				}
				
			} // end: if model.walletIsInitialized
				
			Spacer()
				
		} // </VStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.edgesIgnoringSafeArea(.bottom)
		.sheet(isPresented: $isModifying) {
		
			ElectrumAddressPopup(
				model: model,
				showing: $isModifying
			).padding()
		}
	}
	
	struct CustomSection<Content>: View where Content: View {
		let header: String
		let content: () -> Content

		init(header: String, fullMode: Bool = true, @ViewBuilder content: @escaping () -> Content) {
			self.header = header
			self.content = content
		}

		var body: some View {
			HStack(alignment: .top) {
				Text(header).bold()
					.frame(width: 90, alignment: .leading)
					.font(.subheadline)
					.foregroundColor(.primaryForeground)
					.padding([.leading, .top, .bottom])

				content()
					.padding()
					.frame(maxWidth: .infinity, alignment: .leading)
			}
			.frame(maxWidth: .infinity, alignment: .leading)

			Divider()
		}
	}
}
*/
struct ElectrumAddressPopup: View {
	
	let model: ElectrumConfiguration.Model
	
	@Binding var showing: Bool
	
	@State var isCustomized: Bool
	@State var addressInput: String
	
	init(
		model: ElectrumConfiguration.Model,
		showing: Binding<Bool>
	) {
		self.model = model
		_showing = showing
		
		_isCustomized = State(initialValue: model.electrumServer.customized)
		
		var initialAddr = ""
		if model.electrumServer.customized && model.electrumServer.host.count > 0 {
			initialAddr = model.electrumServer.address()
		}
		
		_addressInput = State(initialValue: initialAddr)
	}

	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				
				Toggle(isOn: $isCustomized) {
					Text("Use a custom server")
				}
				.padding(.bottom, 10)
				.frame(maxWidth: .infinity)
				
			//	Text("Server address:")
			//		.foregroundColor(Color.appHorizon)
			//		.padding(.leading, 16)
			//		.padding(.bottom, -8.0)

				HStack {
					TextField("ServerHost:Port", text: $addressInput)
						.disableAutocorrection(true)
					
					Button {
						addressInput = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(.secondary)
					}
					.isHidden(!isCustomized || addressInput == "")
				}
				.disabled(!isCustomized)
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.background(
					Capsule()
						.stroke(Color(UIColor.separator))
				)

				Text("Note: Server must have a valid certificate")
					.italic()
					.font(.footnote)
					.padding(.top, 10)
			}
			
			Spacer()
			
			HStack {
				Button("OK") {
					showing = false
				//	popoverState.close.send()
				}
				.font(.title2)
			}
			
		} // </VStack>
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

	static var previews: some View {
		mockView(ElectrumConfigurationView())
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
		
		mockView(ElectrumConfigurationView())
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
		
//		ElectrumAddressPopup(model: mockModel)
//			.padding()
//			.preferredColorScheme(.light)
//			.previewDevice("iPhone 8")
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
