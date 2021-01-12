import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ReceiveView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ReceiveView: View {

	@StateObject var qrCode = QRCode()

	@State var sharing: String? = nil
	@State var editing: Bool = false
	@State var unit: String = "sat"

	@State var pushPermissionRequestedFromOS = true
	@State var bgAppRefreshDisabled = false
	@State var notificationsDisabled = false
	@State var alertsDisabled = false
	@State var badgesDisabled = false
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	@StateObject var toast = Toast()
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	var body: some View {
	
		MVIView({ $0.receive() }, onModel: { change in
			
			if let m = change.newModel as? Receive.ModelGenerated {
				qrCode.generate(value: m.request)
			}
		
		}) { model, postIntent in
			
			view(model: model, postIntent: postIntent)
			//	.frame(maxWidth: .infinity, maxHeight: .infinity)
				.navigationBarTitle("Receive ", displayMode: .inline)
				.onAppear {
					postIntent(Receive.IntentAsk(amount: nil, unit: BitcoinUnit.satoshi, desc: nil))
				}
        }
    }
	
	@ViewBuilder
	func view(
		model: Receive.Model,
		postIntent: @escaping (Receive.Intent) -> Void
	) -> some View {
		
		ZStack {
			
			Image("testnet_bg")
				.resizable(resizingMode: .tile)
			
			VStack {
				qrCodeView(model)
					.frame(width: 200, height: 200)
					.padding()
					.background(Color.white)
					.cornerRadius(20)
					.overlay(
						RoundedRectangle(cornerRadius: 20)
							.stroke(Color.appHorizon, lineWidth: 1)
					)
					.padding()
				
				VStack(alignment: .center) {
				
					Text(invoiceAmount(model))
						.font(.caption2)
						.foregroundColor(.secondary)
						.padding(.bottom, 2)
				
					Text(invoiceDescription(model))
						.lineLimit(1)
						.font(.caption2)
						.foregroundColor(.secondary)
						.padding(.bottom, 2)
				}
				.padding([.leading, .trailing], 20)

				HStack {
					actionButton(image: Image(systemName: "square.on.square")) {
						if let m = model as? Receive.ModelGenerated {
							UIPasteboard.general.string = m.request
							toast.toast(text: "Copied in pasteboard!")
						}
					}
					.disabled(!(model is Receive.ModelGenerated))
					
					actionButton(image: Image(systemName: "square.and.arrow.up")) {
						if let m = model as? Receive.ModelGenerated {
							sharing = "lightning:\(m.request)"
						}
					}
					.sharing($sharing)
					.disabled(!(model is Receive.ModelGenerated))
					
					actionButton(image: Image(systemName: "square.and.pencil")) {
						withAnimation {
							editing = true
						}
					}
					.disabled(!(model is Receive.ModelGenerated))
				}
				
				// There are warnings we may want to display:
				//
				// 1. The user has disabled Background App Refresh.
				//    In this case, we won't be able to receive payments
				//    while the app is in the background.
				//
				// 2. When we first prompted them to enable push notifications,
				//    the user said "no". Thus we have never tried to enable push notifications.
				//
				// 3. The user has totally disabled notifications for our app.
				//    So if a payment is received while the app is in the background,
				//    we won't be able to notify them (in any way, shape or form).
				//
				// 4. Similarly to above, the user didn't totally disable notifications,
				//    but they effectively did. Because they disabled all alerts & badges.
				
				if bgAppRefreshDisabled {
					Button {
						showBgAppRefreshDisabledWarning()
					} label: {
						Image(systemName: "exclamationmark.octagon.fill")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.foregroundColor(Color.appRed)
							.frame(width: 32, height: 32)
					}
					.padding(.top, 2)
				}
				else if !pushPermissionRequestedFromOS {
					Button {
						showRequestPushPermissionPopup()
					} label: {
						Image(systemName: "exclamationmark.bubble.fill")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: 32, height: 32)
					}
					.padding(.top, 2)
				}
				else if notificationsDisabled || (alertsDisabled && badgesDisabled) {
					Button {
						showNotificationsDisabledWarning()
					} label: {
						Image(systemName: "exclamationmark.triangle.fill")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.foregroundColor(Color.appYellow)
							.frame(width: 32, height: 32)
					}
					.padding(.top, 2)
				}
				
				Spacer()
				
			} // </VStack>
			.padding(.bottom, keyWindow?.safeAreaInsets.bottom) // top is nav bar
			
			toast.view()
			
		} // </ZStack>
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.onAppear {
			onAppear()
		}
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			willEnterForeground()
		})
		.sheet(isPresented: $editing) {
		
			if let m = model as? Receive.ModelGenerated {
				ModifyInvoicePopup(
					show: $editing,
					amount: m.amount?.description ?? "",
					unit: m.unit,
					desc: m.desc ?? "",
					postIntent: postIntent
				)
			}
		}
	}
	
	@ViewBuilder
	func qrCodeView(_ model: Receive.Model) -> some View {
		
		if let m = model as? Receive.ModelGenerated,
		   qrCode.value == m.request,
		   let image = qrCode.image
		{
			image.resizable()
		} else {
			VStack {
				// Remember: This view is on a white background. Even in dark mode.
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appHorizon))
					.padding(.bottom, 10)
			
				Text("Generating QRCode...")
					.foregroundColor(Color(UIColor.darkGray))
					.font(.caption)
			}
		}
	}

	@ViewBuilder
	func actionButton(
		image: Image,
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			image
				.renderingMode(.template)
				.resizable()
				.scaledToFit()
				.frame(width: 20, height: 20)
				.padding(10)
				.background(Color.buttonFill)
				.cornerRadius(50)
				.overlay(
					RoundedRectangle(cornerRadius: 50)
						.stroke(Color(UIColor.separator), lineWidth: 1)
				)
		}
		.padding()
	}
	
	func invoiceAmount(_ model: Receive.Model) -> String {
		
		if let m = model as? Receive.ModelGenerated {
			if let amount = m.amount {
				return "\(amount.description) \(m.unit.abbrev)"
			} else {
				return NSLocalizedString("any amount",
					comment: "placeholder: invoice is amount-less"
				)
			}
		} else {
			return ""
		}
	}
	
	func invoiceDescription(_ model: Receive.Model) -> String {
		
		if let m = model as? Receive.ModelGenerated {
			if let desc = m.desc, desc.count > 0 {
				return desc
			} else {
				return NSLocalizedString("no description",
					comment: "placeholder: invoice is description-less"
				)
			}
		} else {
			return ""
		}
	}
	
	func onAppear() -> Void {
		log.trace("[ReadyReceivePayment] onAppear()")
		
		let query = Prefs.shared.pushPermissionQuery
		if query == .neverAskedUser {
			
			// We want to ask the user:
			// "Do you want to be notified when you've received a payment?"
			//
			// But let's show the popup after a brief delay,
			// to allow the user to see what this view is about.
			
			DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
				showRequestPushPermissionPopup()
			}
			
		} else {
			
			checkPushPermissions()
		}
	}
	
	func willEnterForeground() -> Void {
		log.trace("[ReadyReceivePayment] willEnterForeground()")
		
		let query = Prefs.shared.pushPermissionQuery
		if query != .neverAskedUser {
			
			checkPushPermissions()
		}
	}
	
	func requestPushPermission() -> Void {
		log.trace("[ReadyReceivePayment] requestPushPermission()")
		
		AppDelegate.get().requestPermissionForLocalNotifications { (granted: Bool) in
			
			let block = { checkPushPermissions() }
			if Thread.isMainThread {
				block()
			} else {
				DispatchQueue.main.async { block() }
			}
		}
	}
	
	func checkPushPermissions() -> Void {
		log.trace("[ReadyReceivePayment] checkPushPermission()")
		assert(Thread.isMainThread, "invoked from non-main thread")
		
		let query = Prefs.shared.pushPermissionQuery
		if query == .userAccepted {
			pushPermissionRequestedFromOS = true
		} else {
			// user denied or ignored, which means we haven't asked the OS for permission yet
			pushPermissionRequestedFromOS = false
		}
		
		bgAppRefreshDisabled = (UIApplication.shared.backgroundRefreshStatus != .available)
		
		let center = UNUserNotificationCenter.current()
		center.getNotificationSettings { settings in
			
			notificationsDisabled = (settings.authorizationStatus != .authorized)
			
			// Are we able to display notifications to the user ?
			// That is, can we display a message that says, "Payment received" ?
			//
			// Within Settings -> Notifications, there are 3 options
			// that can be enabled/disabled independently of each other:
			//
			// - Lock Screen
			// - Notification Center
			// - Banners
			//
			// If the user has disabled ALL of them, then we consider notifications to be disabled.
			// If the user has only disabled one, such as the Lock Screen,
			// we consider that an understandable privacy choice,
			// and don't highlight it in the UI.
			
			var count = 0
			if settings.lockScreenSetting == .enabled {
				count += 1
			}
			if settings.notificationCenterSetting == .enabled {
				count += 1
			}
			if settings.alertSetting == .enabled {
				count += 1
			}
			
			alertsDisabled = (count == 0)
			badgesDisabled = (settings.badgeSetting != .enabled)
		}
		
	}
	
	func showBgAppRefreshDisabledWarning() -> Void {
		log.trace("showBgAppRefreshDisabledWarning()")
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			BgAppRefreshDisabledWarning().anyView
		)
	}
	
	func showNotificationsDisabledWarning() -> Void {
		log.trace("showNotificationsDisabledWarning()")
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			NotificationsDisabledWarning().anyView
		)
	}
	
	func showRequestPushPermissionPopup() -> Void {
		log.trace("showRequestPushPermissionPopup()")
		
		let callback = {(response: PushPermissionPopupResponse) -> Void in
			
			log.debug("PushPermissionPopupResponse: \(response.rawValue)")
			
			switch response {
			case .accepted :
				Prefs.shared.pushPermissionQuery = .userAccepted
				requestPushPermission()
				
			case .denied:
				Prefs.shared.pushPermissionQuery = .userDeclined
				checkPushPermissions()
				
			case .ignored:
				// Ask again next time
				checkPushPermissions()
			}
		}
		
		popoverState.dismissable.send(true)
		popoverState.displayContent.send(
			RequestPushPermissionPopup(callback: callback).anyView
		)
	}
}

struct ModifyInvoicePopup: View {

	@Binding var show: Bool

	@State var amount: String
	@State var unit: BitcoinUnit
	@State var desc: String

	@State var invalidAmount: Bool = false

	let postIntent: (Receive.Intent) -> Void

	var body: some View {
		VStack(alignment: .leading) {
			Text("Edit payment request")
				.font(.title3)
				.padding()

			HStack {
				TextField("Amount (optional)", text: $amount)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.onChange(of: amount) {
						invalidAmount = !$0.isEmpty && (Double($0) == nil || Double($0)! < 0)
					}
					.foregroundColor(invalidAmount ? Color.red : Color.primary)
					.padding([.top, .bottom], 8)
					.padding(.leading, 16)
					.padding(.trailing, 0)

				Picker(selection: $unit, label: Text(unit.abbrev).frame(width: 50)) {
					ForEach(0..<BitcoinUnit.default().values.count) {
						let u = BitcoinUnit.default().values[$0]
						Text(u.abbrev).tag(u)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.padding(.trailing, 2)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))
			.padding([.leading, .trailing])

			HStack {
				TextField("Description (optional)", text: $desc)
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))
			.padding([.leading, .trailing])
			
			Spacer()
			HStack {
				Spacer()
				Button("Done") {
					saveChanges()
					withAnimation { show = false }
				}
				.font(.title2)
				.disabled(invalidAmount)
			}
			.padding()

		} // </VStack>
		
	} // </body>
	
	// Question: Is it possible to call this if the user manually dismisses the view ?
	func saveChanges() -> Void {
		if !invalidAmount {
			postIntent(
				Receive.IntentAsk(
					amount : amount.isEmpty ? nil : KotlinDouble(value: Double(amount)!),
					unit   : unit,
					desc   : desc.isEmpty ? nil : desc
				)
			)
		}
	}
	
} // </ModifyInvoicePopup>


struct BgAppRefreshDisabledWarning: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled Background App Refresh for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"This means you will not be able to receive payments when Phoenix is in the background.  To receive payments, Phoenix must be open and in the foreground."
				)
				.lineLimit(nil)
				.minimumScaleFactor(0.5) // problems with "foreground" being truncated
				.padding(.bottom, 4)
				
				Text(
					"To fix this re-enable Background App Refresh via: Settings -> General -> Background App Refresh"
				)
				
			}
			.padding(.bottom)
			
			HStack {
				Button {
					popoverState.close.send()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close.send()
		})
	}
}

struct NotificationsDisabledWarning: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled notifications for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"This means you will not be notified if you receive a payment while" +
					" Phoenix is in the background."
				)
			}
			.padding(.bottom)
			
			HStack {
				Button {
					fixIt()
					popoverState.close.send()
				} label : {
					Text("Settings").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					popoverState.close.send()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close.send()
		})
	}
	
	func fixIt() -> Void {
		log.trace("[NotificationsDisabledWarning] fixIt()")
		
		if let bundleIdentifier = Bundle.main.bundleIdentifier,
		   let appSettings = URL(string: UIApplication.openSettingsURLString + bundleIdentifier)
		{
			if UIApplication.shared.canOpenURL(appSettings) {
				UIApplication.shared.open(appSettings)
			}
		}
	}
}

enum PushPermissionPopupResponse: String {
	case ignored
	case denied
	case accepted
}

struct RequestPushPermissionPopup: View {
	
	let callback: (PushPermissionPopupResponse) -> Void
	
	@State private var userIsIgnoringPopover: Bool = true
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("Would you like to be notified when somebody sends you a payment?")
			}
			.padding(.bottom)
			
			HStack {
				Button {
					didDeny()
				} label : {
					Text("No").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					didAccept()
				} label : {
					Text("Yes").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(popoverState.close) {
			willClose()
		}
	}
	
	func didDeny() -> Void {
		log.trace("[RequestPushPermissionPopup] didDeny()")
		
		callback(.denied)
		userIsIgnoringPopover = false
		popoverState.close.send()
	}
	
	func didAccept() -> Void {
		log.trace("[RequestPushPermissionPopup] didAccept()")
		
		callback(.accepted)
		userIsIgnoringPopover = false
		popoverState.close.send()
	}
	
	func willClose() -> Void {
		log.trace("[RequestPushPermissionPopup] willClose()")
		
		if userIsIgnoringPopover {
			callback(.ignored)
		}
	}
}

struct FeePromptPopup : View {
	
	@Binding var show: Bool
	let postIntent: (Receive.Intent) -> Void
	
	var body: some View {
		VStack(alignment: .leading) {
			
			Text("Receive with a Bitcoin address")
				.font(.system(.title3, design: .serif))
				.lineLimit(nil)
				.padding(.bottom, 20)
			
			VStack(alignment: .leading, spacing: 14) {
			
				Text("A standard Bitcoin address will be displayed next.")
					
				Text("Funds sent to this address will be shown on your wallet after one confirmation.")
				
				Text("There is a small fee: ") +
				Text("0.10%").bold() +
				Text("\nFor example, to receive $100, the fee is 10 cents.")
			}
			
			HStack {
				Spacer()
				Button("Cancel") {
					withAnimation { show = false }
				}
				.font(.title3)
				.padding(.trailing, 8)
				
				Button("Proceed") {
					withAnimation { show = false }
				}
				.font(.title3)
			}
			.padding(.top, 20)
			
		} // </VStack>
		.padding()
	}
	
} // </FeePromptPopup>

// MARK:-

class ReceiveView_Previews: PreviewProvider {

    static let mockModel = Receive.ModelGenerated(
            request: "lntb17u1p0475jgpp5f69ep0f2202rqegjeddjxa3mdre6ke6kchzhzrn4rxzhyqakzqwqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp2xqrrss9qy9qsqsp5nhhdgpz3549mll70udxldkg48s36cj05epp2cjjv3rrvs5hptdfqlq6h3tkkaplq4au9tx2k49tcp3gx7azehseq68jums4p0gt6aeu3gprw3r7ewzl42luhc3gyexaq37h3d73wejr70nvcw036cde4ptgpckmmkm",
            amount: 0.017,
            unit: BitcoinUnit.millibitcoin,
            desc: "1 Espresso Coin Panna"
    )
//    static let mockModel = Receive.ModelAwaiting()

    static var previews: some View {
        mockView(ReceiveView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
