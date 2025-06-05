import SwiftUI
import PhoenixShared

fileprivate let filename = "MainView_Big"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum LeadingSidebarContent {
	case settings
	case transactions
}

fileprivate enum TrailingSidebarContent {
	case currencyConverter
}

struct MainView_Big: View {
	
	@Namespace var splitView_leadingSidebar
	@Namespace var splitView_primary
	@Namespace var splitView_trailingSidebar
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@State private var wasIPadLandscapeFullscreen = false
	
	@State private var leadingSidebarContent: LeadingSidebarContent? = nil
	@State private var trailingSidebarContent: TrailingSidebarContent? = nil
	
	@State var sidebarWasOpen: Bool = false
	@State var settingsViewTop: Bool = true
	@State var settingsViewRendered: Bool = false
	@State var transactionsViewRendered: Bool = false
	
	let sidebarDividerWidth: CGFloat = 1
	
	let headerButtonPaddingTop: CGFloat = 15
	let headerButtonPaddingBottom: CGFloat = 15
	
	let headerButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<HeaderButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var headerButtonHeight: CGFloat? = nil
	
	@StateObject var navCoordinator_settings = NavigationCoordinator()
	@StateObject var navCoordinator_transactions = NavigationCoordinator()
	@StateObject var navCoordinator_currencyConverter = NavigationCoordinator()
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.top) {
			Color.primaryBackground.ignoresSafeArea()
			content()
			floatingHeader()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: deviceInfo.windowSize) { newSize in
			windowSizeDidChange(newSize)
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
	}
	
	@ViewBuilder
	func floatingHeader() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			// Leading Button 1
			floatingHeader_settingsButton()
				.padding(.trailing)
				.if(hideLeadingSidebarButtons) { view in
					view.opacity(0.0)
				}
			
			// Leading Button 2
			floatingHeader_transactionsButton()
				.if(hideLeadingSidebarButtons) { view in
					view.opacity(0.0)
				}
			
			Spacer(minLength: 0)
			
			// Trailing Button 2
			AppStatusButton(
				headerButtonHeightReader: headerButtonHeightReader,
				headerButtonHeight: $headerButtonHeight
			)
			.padding(.trailing)
			.if(hideTrailingSidebarButtons) { view in
				view.opacity(0.0)
			}
			
			// Trailing Button 1
			if isShowingTrailingSidebar {
				ToolsButton(
					buttonHeightReader: headerButtonHeightReader,
					buttonHeight: $headerButtonHeight,
					action: { closeTrailingSidebar() }
				)
				.if(hideTrailingSidebarButtons) { view in
					view.opacity(0.0)
				}
			} else {
				ToolsMenu(
					buttonHeightReader: headerButtonHeightReader,
					buttonHeight: $headerButtonHeight,
					openCurrencyConverter: { openCurrencyConverter() }
				)
				.if(hideTrailingSidebarButtons) { view in
					view.opacity(0.0)
				}
			}
		}
		.padding(.top, deviceInfo.windowSafeArea.top)
		.padding(.top, headerButtonPaddingTop)
		.padding(.bottom, headerButtonPaddingBottom)
		.padding(.horizontal)
		.assignMaxPreference(for: headerButtonHeightReader.key, to: $headerButtonHeight)
		.frame(maxWidth: deviceInfo.windowSize.width)
		//^^^^(this shouldn't be needed, but doesn't work properly without it; test: 1/3 portrait)
	}
	
	@ViewBuilder
	func floatingHeader_settingsButton() -> some View {
		
		Button {
			toggleIsShowingSettings()
		} label: {
			Image(systemName: "gearshape.fill")
				.renderingMode(.template)
				.imageScale(.large)
				.font(.caption2)
				.foregroundColor(isShowingSettings ? .white : .primary)
				.padding(.all, 7)
				.read(headerButtonHeightReader)
				.frame(minHeight: headerButtonHeight)
				.squareFrame()
				.background(isShowingSettings ? Color.accentColor : Color.buttonFill)
				.cornerRadius(30)
				.overlay(
					RoundedRectangle(cornerRadius: 30)
						.stroke(Color.borderColor, lineWidth: 1)
				)
		}
	}
	
	@ViewBuilder
	func floatingHeader_transactionsButton() -> some View {
		
		Button {
			toggleIsShowingTransactions()
		} label: {
			Image(systemName: "list.bullet")
				.renderingMode(.template)
				.imageScale(.large)
				.font(.caption2)
				.foregroundColor(isShowingTransactions ? .white : .primary)
				.padding(.all, 7)
				.read(headerButtonHeightReader)
				.frame(minHeight: headerButtonHeight)
				.squareFrame()
				.background(isShowingTransactions ? Color.accentColor : Color.buttonFill)
				.cornerRadius(30)
				.overlay(
					RoundedRectangle(cornerRadius: 30)
						.stroke(Color.borderColor, lineWidth: 1)
				)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		// Architecture Notes:
		//
		// If you remove a view from the view hieararchy, then SwiftUI will reset it's state.
		// In the context of the SettingsView/TransactionsView, this means popping back to the rootView.
		//
		// But a similar problem occurs if you move a view from one container to another.
		// For example, in our first attempt, we had code like this:
		//
		// ```
		// if deviceInfo.isIPadLandscapeFullscreen {
		//	  HStack(alignment: VerticalAlignment.center, spacing: 0) {
		//     sidebar().id(splitView_sidebar)
		//     main().id(splitView_detail)
		//   }
		// } else {
		//   ZStack {
		//     main().id(splitView_detail).zIndex(1)
		//     sidebar().id(splitView_sidebar).zIndex(2)
		//   }
		// }
		// ```
		//
		// You might imagine that SwiftUI will consider the instances of the sidebar to be the same.
		// However it doesn't work like this. SwiftUI considers both the id **AND** view hierarchy when
		// determining if a view is the same. The `.id()` is mostly only useful to help SwiftUI detect
		// that views are different. And thus a container change also resets the state (i.e. pops to root view).
		//
		// And so the best solution is to keep the containers static, and alter the frame & offset accordingly.
		
		ZStack(alignment: Alignment.center) {
			
			primary()
				.frame(width: primaryWidth, height: deviceInfo.windowSize.height)
				.offset(x: primaryOffsetX, y: 0)
				.id(splitView_primary)
				.zIndex(0)
			
			if isShowingEitherSidebar && !deviceInfo.isIPadLandscapeFullscreen {
				Color.primary.opacity(0.4)
					.edgesIgnoringSafeArea(.all)
					.zIndex(1)
					.transition(.opacity)
					.onTapGesture {
						withAnimation {
							leadingSidebarContent = nil
							trailingSidebarContent = nil
						}
					}
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer(minLength: 0)
				trailingSidebar()
					.offset(x: trailingSidebarOffsetX, y: 0)
					.id(splitView_trailingSidebar)
				
			}
			.zIndex(2)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				leadingSidebar()
					.offset(x: leadingSidebarOffsetX, y: 0)
					.id(splitView_leadingSidebar)
				Spacer(minLength: 0)
			}
			.zIndex(3)
			
		} // </ZStack>
	}
	
	@ViewBuilder
	func leadingSidebar() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			let contentWidth = leadingSidebarContentWidth
			ZStack {
				if settingsViewRendered || isShowingSettings {
					leadingSidebar_settings()
						.zIndex(settingsViewZIndex)
						.if(settingsViewNeedsTransition) { view in
							view.transition(.move(edge: .leading))
							// ^ should animate with grandparent HStack, but doesn't
						}
						.onAppear { settingsViewRendered = true }
				}
				
				if transactionsViewRendered || isShowingTransactions {
					leadingSidebar_transactions()
						.zIndex(transactionsViewZIndex)
						.if(transactionsViewNeedsTransition) { view in
							view.transition(.move(edge: .leading))
							// ^ should animate with grandparent HStack, but doesn't
						}
						.onAppear { transactionsViewRendered = true }
				}
			}
			.frame(width: contentWidth)
			.frame(maxHeight: .infinity)
			.background(Color.primaryBackground)
			
			if contentWidth < deviceInfo.windowSize.width {
				Divider()
					.frame(width: sidebarDividerWidth)
					.frame(maxHeight: .infinity)
					.background(Color(UIColor.opaqueSeparator))
			}
		}
	}
	
	@ViewBuilder
	func leadingSidebar_settings() -> some View {
		
		NavigationStack(path: $navCoordinator_settings.path) {
			ConfigurationView()
		}
		.environmentObject(navCoordinator_settings)
		.padding(.top, navigationViewPaddingTop)
	}
	
	@ViewBuilder
	func leadingSidebar_transactions() -> some View {
		
		NavigationStack(path: $navCoordinator_transactions.path) {
			TransactionsView()
		}
		.environmentObject(navCoordinator_transactions)
		.padding(.top, navigationViewPaddingTop)
	}
	
	@ViewBuilder
	func primary() -> some View {
		
		MainView_BigPrimary()
			.padding(.top, navigationViewPaddingTop)
	}
	
	@ViewBuilder
	func trailingSidebar() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			let contentWidth = trailingSidebarContentWidth
			if contentWidth < deviceInfo.windowSize.width {
				Divider()
					.frame(width: sidebarDividerWidth)
					.frame(maxHeight: .infinity)
					.background(Color(UIColor.opaqueSeparator))
			}
			
			ZStack {
				if isShowingCurrencyConverter {
					trailingSidebar_currencyConverter()
						.transition(.move(edge: .trailing)) // should animate with grandparent HStack, but doesn't
				}
			}
			.frame(width: contentWidth)
			.frame(maxHeight: .infinity)
			.background(Color.primaryBackground)
		}
	}
	
	@ViewBuilder
	func trailingSidebar_currencyConverter() -> some View {
		
		NavigationStack(path: $navCoordinator_currencyConverter.path) {
			CurrencyConverterView()
		}
		.environmentObject(navCoordinator_currencyConverter)
		.padding(.top, navigationViewPaddingTop)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var isShowingLeadingSidebar: Bool {
		return leadingSidebarContent != nil
	}
	
	var isShowingTrailingSidebar: Bool {
		return trailingSidebarContent != nil
	}
	
	var isShowingEitherSidebar: Bool {
		return isShowingLeadingSidebar || isShowingTrailingSidebar
	}
	
	var isShowingSettings: Bool {
		return leadingSidebarContent == .settings
	}
	
	var isShowingTransactions: Bool {
		return leadingSidebarContent == .transactions
	}
	
	var isShowingCurrencyConverter: Bool {
		return trailingSidebarContent == .currencyConverter
	}
	
	var leadingSidebarContentWidth: CGFloat {
		// We initially hardcoded this to 375.
		// But on some devices it looks a little off because it's just slightly more than half the screen width.
		// So if we're close, we'll round up or down to make it exact, which looks cleaner.
		
		let preferred: CGFloat = 375
		let fullWidth: CGFloat = deviceInfo.windowSize.width
		let halfWidth: CGFloat = fullWidth / 2.0
		
		let diffFromFull = abs(fullWidth - preferred)
		let diffFromHalf = abs(halfWidth - preferred)
		
		if diffFromHalf < 15.0 {
			return halfWidth - sidebarDividerWidth
		} else if (preferred > fullWidth) || (diffFromFull < 25) {
			return fullWidth
		} else {
			return preferred
		}
	}
	
	var leadingSidebarWidth: CGFloat {
		let contentWidth = leadingSidebarContentWidth
		if contentWidth < deviceInfo.windowSize.width {
			return contentWidth + sidebarDividerWidth
		} else {
			return contentWidth
		}
	}
	
	var leadingSidebarOffsetX: CGFloat {
		if isShowingLeadingSidebar {
			return 0
		} else {
			return leadingSidebarWidth * -1.0
		}
	}
	
	var trailingSidebarContentWidth: CGFloat {
		
		let preferred: CGFloat = 320
		let fullWidth: CGFloat = deviceInfo.windowSize.width
		let halfWidth: CGFloat = fullWidth / 2.0
		
		let diffFromFull = abs(fullWidth - preferred)
		let diffFromHalf = abs(halfWidth - preferred)
		
		if diffFromHalf < 15.0 {
			return halfWidth - sidebarDividerWidth
		} else if (preferred > fullWidth) || (diffFromFull < 25) {
			return fullWidth
		} else {
			return preferred
		}
	}
	
	var trailingSidebarWidth: CGFloat {
		let contentWidth = trailingSidebarContentWidth
		if contentWidth < deviceInfo.windowSize.width {
			return contentWidth + sidebarDividerWidth
		} else {
			return contentWidth
		}
	}
	
	var trailingSidebarOffsetX: CGFloat {
		if isShowingTrailingSidebar {
			return 0
		} else {
			return trailingSidebarWidth
		}
	}
	
	var hideLeadingSidebarButtons: Bool {
		
		guard isShowingTrailingSidebar else {
			return false
		}
		
		// The trailingSidebar is open. So the question is:
		// - is there enough width to also open the leading sidebar, without the two overlapping ?
		
		let remainingWidth = deviceInfo.windowSize.width - trailingSidebarWidth
		return (remainingWidth - leadingSidebarWidth) < 10
	}
	
	var hideTrailingSidebarButtons: Bool {
		
		guard isShowingLeadingSidebar else {
			return false
		}
		
		// The leadingSidebar is open. So the question is:
		// - is there enough width to also open the trailing sidebar, without the two overlapping ?
		
		let remainingWidth = deviceInfo.windowSize.width - leadingSidebarWidth
		return (remainingWidth - trailingSidebarWidth) < 10
	}
	
	var navigationViewPaddingTop: CGFloat {
		// Note that NavigationView has some oddities:
		// - if you set its padding.top to zero, it will take into account device.safeArea.top,
		//   draw its content outside the safeArea, and draw its backgroundColor inside the safeArea.
		// - if you set its padding.top to device.safeArea.top, it does exactly the same as a zero padding.
		// - if you set its padding.top to (device.safeArea.top + 1), it will act as you expect,
		//   and draw its backgroundColor where you expect, outside the safeArea.
		
		return deviceInfo.windowSafeArea.top +
		       headerButtonPaddingTop + (headerButtonHeight ?? 0) + headerButtonPaddingBottom
	}
	
	var settingsViewZIndex: Double {
		if settingsViewTop {
			return 1
		} else {
			return 0
		}
	}
	
	var settingsViewNeedsTransition: Bool {
		
		// The `leadingSidebar_content()` has an unwanted animation artifact in certain circumstances:
		// - launch app
		// - open transactions view in leading sidebar
		// - then tap settings button
		// You'll notice that the settings view slides in from the left,
		// since it's being added to the view hierarchy for the first time.
		//
		// This logic is used to disable the transition.
		//
		// Notice that this is a bit tricky.
		// We want to say something like this:
		//
		// if sidebarIsOpen && transactionsViewIsShowing && !settingsViewRendered {
		//     return false
		// }
		//
		// But once the user taps the button to show the settings view:
		// - transactionsViewIsShowing => false
		// - settingsViewIsShowing => true
		//
		// These values change before the animation starts.
		// So we're using a @State that gets toggled after the animation ends.
		
		if !settingsViewRendered && sidebarWasOpen {
			return false
		} else {
			return true
		}
	}
	
	var transactionsViewZIndex: Double {
		if settingsViewTop {
			return 0
		} else {
			return 1
		}
	}
	
	var transactionsViewNeedsTransition: Bool {
		
		if !transactionsViewRendered && sidebarWasOpen {
			return false
		} else {
			return true
		}
	}
	
	var primaryWidth: CGFloat {
		
		var width = deviceInfo.windowSize.width
		if deviceInfo.isIPadLandscapeFullscreen {
			if isShowingLeadingSidebar {
				width = width - leadingSidebarWidth
			}
			if isShowingTrailingSidebar {
				width = width - trailingSidebarWidth
			}
		}
		return width
	}
	
	var primaryOffsetX: CGFloat {
		if deviceInfo.isIPadLandscapeFullscreen {
			var offset: CGFloat = 0
			if isShowingLeadingSidebar {
				offset += leadingSidebarWidth
			}
			if isShowingTrailingSidebar {
				offset -= trailingSidebarWidth
			}
			return offset / 2.0
		} else {
			return 0
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	private func windowSizeDidChange(_ windowSize: CGSize) {
		log.trace("windowSizeDidChange() => w(\(windowSize.width)) height(\(windowSize.height))")
		
		if wasIPadLandscapeFullscreen && !deviceInfo.isIPadLandscapeFullscreen {
			// Transitioning away from landscapeFullscreen.
			// The common thing to do here is to hide the leading sidebar.
			if leadingSidebarContent != nil {
				leadingSidebarContent = nil
			}
		
		} else if isShowingLeadingSidebar && isShowingTrailingSidebar {
			let totalWidth = leadingSidebarWidth + trailingSidebarWidth
			if (totalWidth + 10) > windowSize.width {
				// Pop leading sidebar.
				// This is the less destructive action,
				// since content in the leading sidebar is simply hidden (not removed from view hierarchy),
				// and can be restored by re-opening the leading sidebar.
				leadingSidebarContent = nil
			}
		}
		
		wasIPadLandscapeFullscreen = deviceInfo.isIPadLandscapeFullscreen
	}
	
	private func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.description ?? "nil")")
		
		if let value = value {
			switch value {
				case .paymentHistory     : showTransactions()
				case .backup             : showSettings()
				case .drainWallet        : showSettings()
				case .electrum           : showSettings()
				case .backgroundPayments : showSettings()
				case .liquiditySettings  : showSettings()
				case .forceCloseChannels : showSettings()
				case .swapInWallet       : showSettings()
				case .finalWallet        : showSettings()
			}
			
			if #available(iOS 17, *) {
				
				switch value {
				case .paymentHistory:
					break
					
				case .backup:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.RecoveryPhrase)
						
				case .drainWallet:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.DrainWallet)
					
				case .electrum:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.ElectrumServer)
					
				case .backgroundPayments:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.PaymentOptions)
					navCoordinator_settings.path.append(PaymentOptionsList.NavLinkTag.BackgroundPaymentsSelector)
					
				case .liquiditySettings:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.ChannelManagement)
					
				case .forceCloseChannels:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.ForceCloseChannels)
					
				case .swapInWallet:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.WalletInfo)
					navCoordinator_settings.path.append(WalletInfoView.NavLinkTag.SwapInWalletDetails)
					
				case .finalWallet:
					navCoordinator_settings.path.removeAll()
					navCoordinator_settings.path.append(ConfigurationList.NavLinkTag.WalletInfo)
					navCoordinator_settings.path.append(WalletInfoView.NavLinkTag.FinalWalletDetails)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func toggleIsShowingSettings() {
		log.trace("toggleIsShowingSettings()")
		
		if isShowingSettings {
			closeLeadingSidebar()
		} else {
			showSettings()
		}
	}
	
	func toggleIsShowingTransactions() {
		log.trace("toggleIsShowingTransactions()")
		
		if isShowingTransactions {
			closeLeadingSidebar()
		} else {
			showTransactions()
		}
	}
	
	func closeLeadingSidebar() {
		log.trace("closeLeadingSidebar()")
		
		withAnimation(.linear(duration: 0.3)) {
			leadingSidebarContent = nil
		}
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.31) {
			sidebarWasOpen = false
		}
	}
	
	func showSettings() {
		log.trace("showSettings()")
		
		withAnimation(.linear(duration: 0.3)) {
			settingsViewTop = true
			leadingSidebarContent = .settings
		}
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.31) {
			sidebarWasOpen = true
		}
	}
	
	func showTransactions() {
		log.trace("showTransactions()")
		
		withAnimation(.linear(duration: 0.3)) {
			settingsViewTop = false
			leadingSidebarContent = .transactions
		}
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.31) {
			sidebarWasOpen = true
		}
		
		// We do this here, and not in TransactionsView.onAppear,
		// because TransactionsView.onAppear only fires on the first appearance.
		// And we need to perform this everytime we show the view.
		//
		if let deepLink = deepLinkManager.deepLink, deepLink == .paymentHistory {
			// Reached our destination
			DispatchQueue.main.async { // iOS 14 issues workaround
				deepLinkManager.unbroadcast(deepLink)
			}
		}
	}
	
	func openCurrencyConverter() {
		log.trace("openCurrencyConverter()")
		
		withAnimation {
			trailingSidebarContent = .currencyConverter
		}
	}
	
	func closeTrailingSidebar() {
		log.trace("closeTrailingSidebar()")
		
		withAnimation {
			trailingSidebarContent = nil
		}
	}
}
