import SwiftUI
import PhoenixShared

fileprivate let filename = "ResetWalletView_Options"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ResetWalletView: MVIView {
	
	enum NavLinkTag: String, Codable {
		case ReviewScreen
	}

	@StateObject var mvi = MVIState({ Biz.business.controllers.closeChannelsConfiguration() })
	
	let walletId = Biz.walletId!
	
	@State var deleteSeedBackup: Bool = false
	@State var deleteTransactionHistory: Bool = false
	
	@State var reviewRequested = false
	
	let backupTransactions_enabled_publisher = Prefs.current.backupTransactions.isEnabledPublisher
	@State var backupTransactions_enabled = Prefs.current.backupTransactions.isEnabled
	
	let backupSeed_enabled_publisher = Prefs.current.backupSeed.isEnabledPublisher
	@State var backupSeed_enabled = Prefs.current.backupSeed.isEnabled
	
	let manualBackup_taskDone_publisher = Prefs.current.backupSeed.manualBackupDonePublisher
	@State var manualBackup_taskDone = Prefs.current.backupSeed.manualBackupDone
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		layers()
			.navigationTitle(NSLocalizedString("Reset wallet", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
		}
		.onReceive(backupTransactions_enabled_publisher) {
			self.backupTransactions_enabled = $0
		}
		.onReceive(backupSeed_enabled_publisher) {
			self.backupSeed_enabled = $0
		}
		.onReceive(manualBackup_taskDone_publisher) { _ in
			self.manualBackup_taskDone = Prefs.current.backupSeed.manualBackupDone
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if mvi.model is CloseChannelsConfiguration.ModelLoading {
				section_loading()
				
			} else if mvi.model is CloseChannelsConfiguration.ModelReady {
				section_balance()
				section_options()
				section_button()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_loading() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle())
				Text("Loading wallet...")
			}
		} // </Section>
	}
	
	@ViewBuilder
	func section_balance() -> some View {
		
		Section(header: Text("Wallet Balance")) {
			
			let (balance_bitcoin, balance_fiat) = formattedBalances()
			
			Group {
				if let balance_fiat = balance_fiat {
					Text(verbatim: balance_bitcoin.string).bold()
					+ Text(verbatim: " (≈ \(balance_fiat.string))")
						.foregroundColor(.secondary)
				} else {
					Text(verbatim: balance_bitcoin.string).bold()
				}
			} // </Group>
			.fixedSize(horizontal: false, vertical: true) // Workaround for SwiftUI bugs
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_options() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Text("Delete all wallet data from this device.")
					.font(.headline)
				
				Text("This will reset the app, as if you had just installed it.")
					.lineLimit(nil)
					.font(.subheadline)
					.foregroundColor(.secondary)
				
			} // </VStack>
			.padding(.vertical, 5)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Label {
					Text("iCloud")
				} icon: {
					Image(systemName: "icloud")
				}
				.foregroundColor(.primary)
				
				Label {
					section_options_iCloud()
				} icon: {
					invisibleImage()
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Label {
					Text("Other")
				} icon: {
					Image(systemName: "server.rack")
				}
				.foregroundColor(.primary)
				
				Label {
					section_options_other()
				} icon: {
					invisibleImage()
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_options_iCloud() -> some View {
			
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				if !backupSeed_enabled {
					
					Label {
						Text("Delete seed backup from my iCloud account.")
							.foregroundColor(.secondary)
							.fixedSize(horizontal: false, vertical: true)
					} icon: {
						checkboxDisabledImage()
							.foregroundColor(.secondary)
					}
					
					Label {
						Text("Seed backup not stored in iCloud.")
							.font(.footnote)
							.foregroundColor(.primary) // Stands out to provide explanation
					} icon: {
						invisibleImage()
					}
					
				} else {
					
					Toggle(isOn: $deleteSeedBackup) {
						Text("Delete seed backup from my iCloud account.")
							.foregroundColor(.primary)
					}
					.toggleStyle(CheckboxToggleStyle(
						onImage: checkboxOnImage(),
						offImage: checkboxOffImage()
					))
				}
				
			} // </VStack>
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				if !backupTransactions_enabled {
					
					Label {
						Text("Delete payment history and contacts from my iCloud account.")
							.foregroundColor(.secondary)
							.fixedSize(horizontal: false, vertical: true)
					} icon: {
						checkboxDisabledImage()
							.foregroundColor(.secondary)
					}
					
					Label {
						Text("Payment history and contacts not stored in iCloud.")
							.font(.footnote)
							.foregroundColor(.primary) // Stands out to provide explanation
					} icon: {
						invisibleImage()
					}
					
				} else {
					
					Toggle(isOn: $deleteTransactionHistory) {
						Text("Delete payment history and contacts from my iCloud account.")
							.foregroundColor(.primary)
					}
					.toggleStyle(CheckboxToggleStyle(
						onImage: checkboxOnImage(),
						offImage: checkboxOffImage()
					))
				}
				
			} // </VStack>
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_options_other() -> some View {
		
		Label {
			Text("We do not store any user-identifying information on our servers.")
		} icon: {
			Image(systemName: "face.smiling")
				.imageScale(.large)
		}
		.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 10) {
				
				Button {
					reviewButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Text("Review")
						Image(systemName: "arrow.forward")
							.imageScale(.small)
					}
				}
				.font(.title3.weight(.medium))
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func checkboxOnImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func checkboxOffImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func checkboxDisabledImage() -> some View {
		Image(systemName: "square.dashed")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.clear)
			.accessibilityHidden(true)
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
		case .ReviewScreen:
			ResetWalletView_Confirm(
				mvi: mvi,
				deleteTransactionHistory: deleteTransactionHistory,
				deleteSeedBackup: deleteSeedBackup
			)
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
	
	func formattedBalances() -> (FormattedAmount, FormattedAmount?) {
		
		let balance_sats = mvi.balanceSats()
		
		let balance_bitcoin = Utils.formatBitcoin(sat: balance_sats, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var balance_fiat: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			balance_fiat = Utils.formatFiat(sat: balance_sats, exchangeRate: exchangeRate)
		}
		
		return (balance_bitcoin, balance_fiat)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func reviewButtonTapped() {
		log.trace("reviewButtonTapped()")
		
		navigateTo(.ReviewScreen)
	}
}
