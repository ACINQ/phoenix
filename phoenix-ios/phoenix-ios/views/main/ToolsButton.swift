import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ToolsButton"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ToolsImage: View {
	
	let isSelected: Bool
	
	let buttonHeightReader: GeometryPreferenceReader<AppendValue<HeaderButtonHeight>, [CGFloat]>
	@Binding var buttonHeight: CGFloat?
	
	@ViewBuilder
	var body: some View {
		
		Image(systemName: "wrench.fill")
			.renderingMode(.template)
			.imageScale(.large)
			.font(.caption2)
			.foregroundColor(isSelected ? .white : .primary)
			.padding(.all, 7)
			.read(buttonHeightReader)
			.frame(minHeight: buttonHeight)
			.squareFrame()
			.background(isSelected ? Color.accentColor : Color.buttonFill)
			.cornerRadius(30)
			.overlay(
				RoundedRectangle(cornerRadius: 30)
					.stroke(Color.borderColor, lineWidth: 1)
			)
	}
}

struct ToolsButton: View {
	
	let toolsImage: ToolsImage
	let action: () -> Void
	
	init(
		buttonHeightReader: GeometryPreferenceReader<AppendValue<HeaderButtonHeight>, [CGFloat]>,
		buttonHeight: Binding<CGFloat?>,
		action: @escaping () -> Void
	) {
		self.toolsImage = ToolsImage(
			isSelected: true,
			buttonHeightReader: buttonHeightReader,
			buttonHeight: buttonHeight
		)
		self.action = action
	}
	
	@ViewBuilder
	var body: some View {
		
		Button {
			action()
		} label: {
			toolsImage
		}
	}
}

struct ToolsMenu: View {
	
	let toolsImage: ToolsImage
	let openCurrencyConverter: () -> Void
	
	@Environment(\.openURL) var openURL
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	init(
		buttonHeightReader: GeometryPreferenceReader<AppendValue<HeaderButtonHeight>, [CGFloat]>,
		buttonHeight: Binding<CGFloat?>,
		openCurrencyConverter: @escaping () -> Void
	) {
		self.toolsImage = ToolsImage(
			isSelected: false,
			buttonHeightReader: buttonHeightReader,
			buttonHeight: buttonHeight
		)
		self.openCurrencyConverter = openCurrencyConverter
	}
	
	var body: some View {
		
		Menu {
			Button {
				currencyConverterTapped()
			} label: {
				Label(
					NSLocalizedString("Currency converter", comment: "HomeView: Tools menu: Label"),
					systemImage: "globe"
				)
			}
			
			Button {
				supportButtonTapped()
			} label: {
				Label(
					NSLocalizedString("Support", comment: "HomeView: Tools menu: Label"),
					image: "email"
				)
			}
			.accessibilityHint("opens browser")
			
			Button {
				faqButtonTapped()
			} label: {
				Label(
					NSLocalizedString("FAQ", comment: "HomeView: Tools menu: Label"),
					systemImage: "safari"
				)
			}
			.accessibilityHint("opens browser")
			
			Button {
				githubButtonTapped()
			} label: {
				Label {
					Text("View source")
				} icon: {
					Image("github")
				}
			}
			.accessibilityHint("opens browser")
			
		} label: {
			toolsImage
		}
		.accessibilityLabel("Tools")
	}
	
	func currencyConverterTapped() {
		log.trace("currencyConverterTapped()")
		
		openCurrencyConverter()
	}
	
//	func sendFeedbackButtonTapped() {
//		log.trace("sendFeedbackButtonTapped()")
//		
//		let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
//		let device = UIDevice.current
//		
//		var body = "Phoenix v\(appVersion ?? "x.y.z") "
//		body += "(\(device.systemName) \(device.systemVersion))"
//		
//		var comps = URLComponents()
//		comps.scheme = "mailto"
//		comps.path = "phoenix@acinq.co"
//		comps.queryItems = [
//			URLQueryItem(name: "subject", value: "Phoenix iOS Feedback"),
//			URLQueryItem(name: "body", value: body)
//		]
//
//		if let url = comps.url {
//			openURL(url)
//		}
//	}
	
	func supportButtonTapped() {
		log.trace("supportButtonTapped")
		
		if let url = URL(string: "https://phoenix.acinq.co/support") {
			openURL(url)
		}
	}
	
	func faqButtonTapped() {
		log.trace("faqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func githubButtonTapped() {
		log.trace("githubButtonTapped()")
		
		if let url = URL(string: "https://github.com/ACINQ/phoenix") {
			openURL(url)
		}
	}
}
