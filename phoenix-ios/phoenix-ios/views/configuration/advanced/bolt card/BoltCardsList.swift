import SwiftUI
import PhoenixShared
import CoreNFC

fileprivate let filename = "BoltCardsList"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct BoltCardsList: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case ManageBoltCard(cardInfo: BoltCardInfo, isNewCard: Bool)
		
		var description: String {
			switch self {
				case .ManageBoltCard(let info, _) : return "ManageBoltCard(\(info.name))"
			}
		}
	}
	
	@State var sortedCards: [BoltCardInfo] = []
	@State var archivedCards: [BoltCardInfo] = []
	
	@State var isFetchingLnurlwAddr: Bool = false
	@State var lnurlwAddrFetchError: Bool = false
	
	@State var archivedCardsHidden: Bool = true
	@State var nfcUnavailable: Bool = false
	@State var showHelpSheet: Bool = false
	@State var didAppear: Bool = false
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Bolt Cards")
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
			.toolbar { toolbarItems() }
	}
	
	@ToolbarContentBuilder
	func toolbarItems() -> some ToolbarContent {
		
		ToolbarItem(placement: .navigationBarTrailing) {
			Menu {
				Button {
					readCard()
				} label: {
					Label {
						Text("Read card…")
					} icon: {
						Image(systemName: "creditcard")
					}
				}
				.disabled(nfcUnavailable)
				
			} label: {
				Image(systemName: "ellipsis")
			}
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			if !sortedCards.isEmpty {
				section_cards()
			}
			if !archivedCards.isEmpty {
				section_archived_cards()
			}
			section_new()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear {
			onAppear()
		}
		.onReceive(Biz.business.cardsManager.cardsListPublisher()) {
			cardsListChanged($0)
		}
		.sheet(isPresented: $showHelpSheet) {
			BoltCardsHelp(isShowing: $showHelpSheet)
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 10) {
				Text("Link a **physical card** to your Phoenix wallet.")
					.multilineTextAlignment(.center)
				
				Text("Then make **contactless payments** at supporting merchants.")
					.multilineTextAlignment(.center)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer(minLength: 0)
					Button {
						showHelpSheet = true
					} label: {
						Text("learn more")
							.font(.callout)
					}
				}
				.padding(.top, 5)
				
			} // </VStack>
			.background(
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Image(systemName: "creditcard").resizable().scaledToFit()
					Spacer()
					Image(systemName: "wave.3.forward").resizable().scaledToFit()
				} // </HStack>
				.opacity(0.03)
			)
		} // </Section>
	}
	
	@ViewBuilder
	func section_cards() -> some View {
		
		Section {
			ForEach(sortedCards) { cardInfo in
				navLink(.ManageBoltCard(cardInfo: cardInfo, isNewCard: false)) {
					section_cards_item(cardInfo)
				}
			}
			
		} header: {
			Text("Linked Cards")
		}
	}
	
	@ViewBuilder
	func section_cards_item(_ cardInfo: BoltCardInfo) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			
			Group {
				if cardInfo.isForeign {
					Image(systemName: "key.radiowaves.forward.fill")
						.resizable()
						.foregroundStyle(Color.white)
				} else {
					Image("boltcard")
						.resizable()
				}
			}
			.scaledToFit()
			.aspectRatio(contentMode: .fit)
			.frame(width: 42, height: 42, alignment: .center)
			.padding(.all, 8)
			.background(Color.black.cornerRadius(8))
			.padding(.trailing, 10)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				
				Text(cardInfo.sanitizedName)
					.lineLimit(1)
					.truncationMode(.tail)
					.font(.title2)
				
				Group {
					if cardInfo.isFrozen {
						Text("Status: Frozen")
					} else {
						Text("Status: Active")
					}
				}
				.foregroundStyle(.secondary)
			}
		}
		.listRowInsets(EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16))
	}
	
	@ViewBuilder
	func section_archived_cards() -> some View {
		
		Section {
			if !archivedCardsHidden {
				ForEach(archivedCards) { cardInfo in
					navLink(.ManageBoltCard(cardInfo: cardInfo, isNewCard: false)) {
						Text(cardInfo.sanitizedName)
					}
				}
			}
			
		} header: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Archived Cards")
				Spacer()
				Button {
					withAnimation {
						archivedCardsHidden.toggle()
					}
				} label: {
					if archivedCardsHidden {
						Image(systemName: "eye")
					} else {
						Image(systemName: "eye.slash")
					}
				}
				.foregroundColor(.secondary)
			}
		}
	}

	@ViewBuilder
	func section_new() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 10) {
				
			#if targetEnvironment(simulator)
				Button {
					showCardOptions()
				} label: {
					Text("Create New Debit Card")
						.font(.title3.weight(.medium))
				}
				.buttonStyle(.borderedProminent)
				.buttonBorderShape(.capsule)
				.disabled(nfcUnavailable || isFetchingLnurlwAddr)
			#else
			#if DEBUG
				Button {/* using simultaneousGesture below */} label: {
					Text("Create New Debit Card")
						.font(.title3.weight(.medium))
				}
				.buttonStyle(.borderedProminent)
				.buttonBorderShape(.capsule)
				.disabled(nfcUnavailable || isFetchingLnurlwAddr)
				.simultaneousGesture(TapGesture().onEnded { _ in
					log.debug("simultaneousGesture: TapGesture")
					createNewDebitCard()
				})
				.simultaneousGesture(LongPressGesture(minimumDuration: 2.0).onEnded { _ in
					log.debug("simultaneousGesture: LongPressGesture")
					showCardOptions()
				})
			#else
				Button {
					createNewDebitCard()
				} label: {
					Text("Create New Debit Card")
						.font(.title3.weight(.medium))
				}
				.buttonStyle(.borderedProminent)
				.buttonBorderShape(.capsule)
				.disabled(nfcUnavailable || isFetchingLnurlwAddr)
			#endif
			#endif
				
				if nfcUnavailable {
					Text("NFC capabilities not available on this device.")
						.multilineTextAlignment(.center)
						.foregroundStyle(Color.appNegative)
				} else if lnurlwAddrFetchError {
					Text("Error fetching registration. Please check internet connection.")
						.multilineTextAlignment(.center)
						.foregroundStyle(Color.appNegative)
				} else if isFetchingLnurlwAddr {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						ProgressView()
							.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
						Text("Preparing system for NFC...")
					}
				}
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
		.listRowBackground(Color.clear)
	}
	
	@ViewBuilder
	private func navLink<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		}
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .ManageBoltCard(let cardInfo, let isNewCard):
			ManageBoltCard(cardInfo: cardInfo, isNewCard: isNewCard)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}

	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			// First time displaying this View
			
		#if targetEnvironment(simulator)
			// We know the simulator doesn't have NFC capabilities.
			// But we have a workaround to support linking a card to a simulator.
			// Which is quite helpful for testing.
		#else
			if !NFCReaderSession.readingAvailable {
				nfcUnavailable = true
			}
		#endif
			
		} else {
			// We are returning to this View
		}
	}
	
	func cardsListChanged(_ updatedList: [BoltCardInfo]) {
		log.trace("cardsListChanged()")
		
		sortedCards = updatedList.filter { !$0.isArchived }.sorted { cardA, cardB in
			// return true if `cardA` should be ordered before `cardB`; otherwise return false
			return (cardA.createdAtDate < cardB.createdAtDate)
		}
		
		archivedCards = updatedList.filter { $0.isArchived }.sorted { cardA, cardB in
			// return true if `cardA` should be ordered before `cardB`; otherwise return false
			return (cardA.createdAtDate < cardB.createdAtDate)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.description))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func showCardOptions() {
		log.trace("showCardOptions()")
		
		smartModalState.display(dismissable: true) {
			CardOptionsSheet(
				didSelectVersion: didSelectVersion,
				didSelectSimulator: didSelectSimulator
			)
		}
	}
	
	func didSelectVersion(_ version: BoltCardVersion) {
		log.trace("didSelectVersion: \(version)")
		
		var missingLnAddress = false
		switch version {
		case .V1:
			fetchLnurlWithdrawAddress(lnAddress: nil)
			
		case .V1AndV2:
			if let lnAddress = AppSecurity.shared.getBip353Address() {
				fetchLnurlWithdrawAddress(lnAddress: lnAddress)
			} else {
				missingLnAddress = true
			}
			
		case .V2:
			if let lnAddress = AppSecurity.shared.getBip353Address() {
				let input = BoltCardInput.V2(lnAddress: lnAddress)
			#if targetEnvironment(simulator)
				presentSimulatorPasteSheet(input)
			#else
				writeToNfcCard(input)
			#endif
			} else {
				missingLnAddress = true
			}
		}
		
		if missingLnAddress {
			// Todo...
		}
	}
	
	func didSelectSimulator() {
		log.trace("didSelectSimulator()")
		
	#if targetEnvironment(simulator)
		log.warning("didSelectSimulator(): ignorning - we are in the simulator")
		return
	#else
		smartModalState.display(dismissable: true) {
			SimulatorWriteSheet()
		}
	#endif
	}
	
	func createNewDebitCard() {
		log.trace("createNewDebitCard()")
		
		if let lnAddress = AppSecurity.shared.getBip353Address() {
			let input = BoltCardInput.V2(lnAddress: lnAddress)
			writeToNfcCard(input)
			
		} else {
			// Todo..
		}
	}
	
	// --------------------------------------------------
	// MARK: Create Card
	// --------------------------------------------------
	
	func fetchLnurlWithdrawAddress(lnAddress: String?) {
		log.trace("fetchLnurlWithdrawAddress()")
		
		// Developer Note:
		// This registration process will **NOT** be needed after we develop the new protocol.
		
		let continueToNextStep = {(registration: LnurlWithdrawRegistration?) in
			
			if let hexAddr = registration?.hexAddr {
				let input: BoltCardInput
				if let lnAddress {
					input = BoltCardInput.V1AndV2(lnurlWithdrawId: hexAddr, lnAddress: lnAddress)
				} else {
					input = BoltCardInput.V1(lnurlWithdrawId: hexAddr)
				}
				
			#if targetEnvironment(simulator)
				presentSimulatorPasteSheet(input)
			#else
				writeToNfcCard(input)
			#endif
			} else {
				lnurlwAddrFetchError = true
			}
		}
		
		if let existingRegistration = LnurlwRegistration.existingRegistration() {
			continueToNextStep(existingRegistration)
		} else {
			isFetchingLnurlwAddr = true
			lnurlwAddrFetchError = false
			
			Task { @MainActor in
				let registration = await LnurlwRegistration.fetchRegistration()
				isFetchingLnurlwAddr = false
				continueToNextStep(registration)
			}
		}
	}
	
	func presentSimulatorPasteSheet(_ input: BoltCardInput) {
		log.trace("presentSimulatorPasteSheet()")
		
		smartModalState.display(dismissable: true) {
			SimulatorPasteSheet(input: input)
		}
	}
	
	func writeToNfcCard(_ cardInput: BoltCardInput) {
		log.trace("writeToNfcCard()")
		
		let lnurlWithdrawBaseUrl = URL(string: "https://phoenix.deusty.com/v1/pub/lnurlw/info")!
		var queryItems: [URLQueryItem] = []
		
		let template: Ndef.Template
		switch cardInput {
		case .V1(let lnurlWithdrawId):
			queryItems.append(URLQueryItem(name: "id", value: lnurlWithdrawId))
			
			var comps = URLComponents(url: lnurlWithdrawBaseUrl, resolvingAgainstBaseURL: false)!
			comps.queryItems = queryItems
			let resolvedUrl = comps.url!
			
			template = Ndef.Template(baseUrl: resolvedUrl)!
			
		case .V1AndV2(let lnurlWithdrawId, let lnAddress):
			queryItems.append(URLQueryItem(name: "id", value: lnurlWithdrawId))
			queryItems.append(URLQueryItem(name: "v2", value: lnAddress))
			
			var comps = URLComponents(url: lnurlWithdrawBaseUrl, resolvingAgainstBaseURL: false)!
			comps.queryItems = queryItems
			let resolvedUrl = comps.url!
			
			template = Ndef.Template(baseUrl: resolvedUrl)!
			
		case .V2(let lnAddress):
			template = Ndef.Template(baseText: lnAddress)
		}
		
		log.debug("template.value: \(template.valueString)")
		log.debug("template.piccDataOffset: \(template.piccDataOffset)")
		log.debug("template.cmacOffset: \(template.cmacOffset)")
		
		let keys = BoltCardKeySet.companion.random()
		let nfcInput = NfcWriter.WriteInput(
			template    : template,
			key0        : keys.key0_bytes,
			piccDataKey : keys.piccDataKey_bytes,
			cmacKey     : keys.cmacKey_bytes
		)

		NfcWriter.shared.writeCard(nfcInput) { (result: Result<NfcWriter.WriteOutput, NfcWriter.WriteError>) in
			
			switch result {
			case .failure(let error):
				log.debug("error: \(error)")
				showWriteErrorSheet(error)
				
			case .success(let output):
				log.debug("output.chipUid: \(output.chipUid.toHex())")
				saveNewCard(keys, output)
			}
		}
	}
	
	func saveNewCard(
		_ keys: BoltCardKeySet,
		_ output: NfcWriter.WriteOutput
	) {
		
		// Conversion madness: [UInt8] -> Data -> ByteArray -> ByteVector
		let uidByteArray = Helper.dataFromBytes(bytes: output.chipUid).toKotlinByteArray()
		let uid = Bitcoin_kmpByteVector(bytes: uidByteArray)
		
		let cardInfo = BoltCardInfo(name: "", keys: keys, uid: uid, isForeign: false)
		
		Task { @MainActor in
			do {
				try await Biz.business.cardsManager.saveCard(card: cardInfo)
				navigateTo(.ManageBoltCard(cardInfo: cardInfo, isNewCard: true))
				
			} catch {
				log.error("CardsManager.saveCard(): error: \(error)")
			}
		}
	}
	
	func showWriteErrorSheet(_ error: NfcWriter.WriteError) {
		log.trace("showWriteErrorSheet()")
		
		var shouldIgnoreError = false
		if case .scanningTerminated(let nfcError) = error {
			shouldIgnoreError = nfcError.isIgnorable()
		}
		
		guard !shouldIgnoreError else {
			log.debug("showWriteErrorSheet(): ignoring standard user error")
			return
		}
		
		smartModalState.display(dismissable: true) {
			WriteErrorSheet(error: error, context: .whileWriting)
		}
	}
	
	// --------------------------------------------------
	// MARK: Read Card
	// --------------------------------------------------
	
	func readCard() {
		log.trace("readCard()")
		
		NfcReader.shared.readCard { (result: Result<NFCNDEFMessage, NfcReader.ReadError>) in
			
			var shouldIgnoreError = false
			if case let .failure(error) = result {
				if case let .scanningTerminated(nfcError) = error {
					shouldIgnoreError = nfcError.isIgnorable()
				}
			}
			
			guard !shouldIgnoreError else {
				log.debug("NfcReader.readCard(): ignoring standard user error")
				return
			}
			
			smartModalState.display(dismissable: true) {
				ReadCardSheet(result: result)
			}
		}
	}
}
