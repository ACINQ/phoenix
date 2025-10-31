import SwiftUI
import PhoenixShared
import CoreNFC

fileprivate let filename = "Experimental"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Experimental: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case ManageBoltCard(cardInfo: BoltCardInfo, isNewCard: Bool)
		
		var description: String {
			switch self {
				case .ManageBoltCard(let info, _) : return "ManageBoltCard(\(info.name))"
			}
		}
	}
	
	@State var address: String? = Keychain.current.getBip353Address()
	@State var isClaimingAddress: Bool = false
	
	enum ClaimError: Error {
		case noChannels
		case timeout
	}
	@State var claimError: ClaimError? = nil
	@State var claimIndex: Int = 0
	
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
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var smartModalState: SmartModalState
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			content()
			toast.view()
		}
		.navigationTitle("Experimental")
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_bip353()
			
			section_cardsInfo()
			if !sortedCards.isEmpty {
				section_linkedCards()
			}
			if !archivedCards.isEmpty {
				section_archivedCards()
			}
			
			section_newCard()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
			navLinkView()
		}
		.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
			navLinkView(tag)
		}
		.toolbar {
			toolbarItems()
		}
		.onAppear {
			onAppear()
		}
		.task {
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				for await list in cardsDb.cardsListSequence() {
					cardsListChanged(list)
				}
			} catch {}
		}
		.sheet(isPresented: $showHelpSheet) {
			BoltCardsHelp(isShowing: $showHelpSheet)
		}
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
	func navLink<Content>(
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
	// MARK: Section: BIP 353
	// --------------------------------------------------
	
	@ViewBuilder
	func section_bip353() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 24) {
				LabelAlignment {
					section_bip353_info()
				} icon: {
					Image(systemName: "at")
				}
				
				if address == nil {
					section_bip353_claim()
				}
				
				if let err = claimError {
					section_bip353_error(err)
				}
			}
			
		} /* Section.*/ header: {
			
			Text("BIP353 DNS Address")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_bip353_info() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			if let address {
				HStack(alignment: VerticalAlignment.top, spacing: 4) {
					Text(address)
					Spacer(minLength: 0)
					Button {
						copyText(address)
					} label: {
						Image(systemName: "square.on.square")
					}
				}
				.font(.headline)
				
			} else {
				Text("No address yet...")
					.font(.headline)
			}
			
			Text(
				"""
				This is a human-readable address for your Bolt12 payment request.
				"""
			)
			.font(.subheadline)
			.foregroundColor(.secondary)
			.padding(.top, 4)
			
			if address != nil {
				Text(
					"""
					Want a prettier address? Use third-party services, or self-host the address!
					"""
				)
				.font(.subheadline)
				.foregroundColor(.secondary)
				.padding(.top, 8)
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_bip353_claim() -> some View {
		
		ZStack(alignment: Alignment.leading) {
			
			if isClaimingAddress {
				Label {
					Text("")
				} icon: {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				}
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer(minLength: 10)
				
				Button {
					claimButtonTapped()
				} label: {
					Text("Claim my address")
				}
				.buttonStyle(.borderedProminent)
				.buttonBorderShape(.capsule)
				.disabled(isClaimingAddress)
				
				Spacer(minLength: 10)
			} // </HStack>
		}
	}
	
	@ViewBuilder
	func section_bip353_error(_ err: ClaimError) -> some View {
		
		Label {
			switch err {
			case .noChannels:
				Text(
					"""
					You need at least one channel to claim your address. \
					Try adding funds to your wallet and try again.
					"""
				)
				
			case .timeout:
				Text(
					"""
					The request timed out. \
					Please check your internet connection and try again.
					"""
				)
			}
		} icon: {
			Image(systemName: "exclamationmark.triangle")
				.foregroundColor(.appNegative)
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: Cards Info
	// --------------------------------------------------
	
	@ViewBuilder
	func section_cardsInfo() -> some View {
		
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
		} header: {
			Text("Bolt Cards")
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: Linked Cards
	// --------------------------------------------------
	
	@ViewBuilder
	func section_linkedCards() -> some View {
		
		Section {
			ForEach(sortedCards) { cardInfo in
				navLink(.ManageBoltCard(cardInfo: cardInfo, isNewCard: false)) {
					section_linkedCards_item(cardInfo)
				}
			}
			
		} header: {
			Text("Linked Cards")
		}
	}
	
	@ViewBuilder
	func section_linkedCards_item(_ cardInfo: BoltCardInfo) -> some View {
		
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
	
	// --------------------------------------------------
	// MARK: Section: Archived Cards
	// --------------------------------------------------
	
	@ViewBuilder
	func section_archivedCards() -> some View {
		
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
	
	// --------------------------------------------------
	// MARK: Section: New Card
	// --------------------------------------------------
	
	@ViewBuilder
	func section_newCard() -> some View {
		
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
		log.trace(#function)
		
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
		log.trace(#function)
		
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
	
	func claimButtonTapped() {
		log.trace(#function)
		
		let channels = Biz.business.peerManager.channelsValue()
		guard !channels.isEmpty else {
			claimError = .noChannels
			return
		}
		
		guard
			let peer = Biz.business.peerManager.peerStateValue(),
			!isClaimingAddress
		else {
			return
		}
		
		isClaimingAddress = true
		claimError = nil
		
		let idx = claimIndex
		let finish = {(result: Result<String, ClaimError>) in
			
			guard self.claimIndex == idx else {
				return
			}
			self.claimIndex += 1
			self.isClaimingAddress = false
			
			switch result {
			case .success(let addr):
				self.address = addr
				self.claimError = nil
				let _ = Keychain.current.setBip353Address(addr)
				
			case .failure(let err):
				self.claimError = err
			}
		}
		
		Task { @MainActor in
			do {
				let addr = try await peer.requestAddress(languageSubtag: "en")
				finish(.success(addr))
				
			} catch {
				finish(.failure(.timeout))
			}
		}
		
		Task { @MainActor in
			try await Task.sleep(seconds: 5)
			finish(.failure(.timeout))
		}
	}
	
	func copyText(_ text: String) -> Void {
		log.trace(#function)
		
		UIPasteboard.general.string = text
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite,
			style: .chrome
		)
	}
	
	func showCardOptions() {
		log.trace(#function)
		
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
			if let lnAddress = Keychain.current.getBip353Address() {
				fetchLnurlWithdrawAddress(lnAddress: lnAddress)
			} else {
				missingLnAddress = true
			}
			
		case .V2:
			if let lnAddress = Keychain.current.getBip353Address() {
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
			smartModalState.display(dismissable: true) {
				PrerequisitesSheet()
			}
		}
	}
	
	func didSelectSimulator() {
		log.trace(#function)
		
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
		log.trace(#function)
		
		if let lnAddress = Keychain.current.getBip353Address() {
			let input = BoltCardInput.V2(lnAddress: lnAddress)
			writeToNfcCard(input)
			
		} else {
			smartModalState.display(dismissable: true) {
				PrerequisitesSheet()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Create Card
	// --------------------------------------------------
	
	func fetchLnurlWithdrawAddress(lnAddress: String?) {
		log.trace(#function)
		
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
		
		let template = cardInput.toTemplate()
		
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
		let uid: Bitcoin_kmpByteVector = output.chipUid.toData().toKotlinByteVector()
		
		let cardInfo = BoltCardInfo(name: "", keys: keys, uid: uid, isForeign: false)
		
		Task { @MainActor in
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				try await cardsDb.saveCard(card: cardInfo)
				navigateTo(.ManageBoltCard(cardInfo: cardInfo, isNewCard: true))
				
			} catch {
				log.error("SqliteCardsDb.saveCard(): error: \(error)")
			}
		}
	}
	
	func showWriteErrorSheet(_ error: NfcWriter.WriteError) {
		log.trace(#function)
		
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
		log.trace(#function)
		
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
