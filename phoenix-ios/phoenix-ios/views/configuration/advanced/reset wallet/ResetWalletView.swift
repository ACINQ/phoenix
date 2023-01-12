import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ResetWalletView_Options"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ResetWalletView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.closeChannelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	let encryptedNodeId = Biz.encryptedNodeId!
	
	@State var deleteSeedBackup: Bool = false
	@State var deleteTransactionHistory: Bool = false
	
	@State var reviewRequested = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let backupTransactions_enabled_publisher = Prefs.shared.backupTransactions.isEnabledPublisher
	@State var backupTransactions_enabled = Prefs.shared.backupTransactions.isEnabled
	
	let backupSeed_enabled_publisher = Prefs.shared.backupSeed.isEnabled_publisher
	@State var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	
	let manualBackup_taskDone_publisher = Prefs.shared.backupSeed.manualBackup_taskDone_publisher
	@State var manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(
		encryptedNodeId: Biz.encryptedNodeId!
	)
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: reviewScreen(),
					isActive: $reviewRequested
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			content()
		}
		.navigationStackDestination(isPresented: $reviewRequested) { // For iOS 16+
			reviewScreen()
		}
		.onReceive(backupTransactions_enabled_publisher) {
			self.backupTransactions_enabled = $0
		}
		.onReceive(backupSeed_enabled_publisher) {
			self.backupSeed_enabled = $0
		}
		.onReceive(manualBackup_taskDone_publisher) {
			self.manualBackup_taskDone =
				Prefs.shared.backupSeed.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
		}
		.navigationTitle(NSLocalizedString("Reset wallet", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
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
					
					if !backupSeed_enabled {
						Label {
							Text("Seed backup not stored in iCloud.")
								.font(.footnote)
								.foregroundColor(.primary) // Stands out to provide explanation
						} icon: {
							invisibleImage()
						}
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
						Text("Delete payment history from my iCloud account.")
							.foregroundColor(.secondary)
							.fixedSize(horizontal: false, vertical: true)
					} icon: {
						checkboxDisabledImage()
							.foregroundColor(.secondary)
					}
					
					if !backupTransactions_enabled {
						Label {
							Text("Payment history not stored in iCloud.")
								.font(.footnote)
								.foregroundColor(.primary) // Stands out to provide explanation
						} icon: {
							invisibleImage()
						}
					}
					
				} else {
					
					Toggle(isOn: $deleteTransactionHistory) {
						Text("Delete payment history from my iCloud account.")
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
	func reviewScreen() -> some View {
		
		ResetWalletView_Confirm(
			mvi: mvi,
			deleteTransactionHistory: deleteTransactionHistory,
			deleteSeedBackup: deleteSeedBackup
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
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
	
	func reviewButtonTapped() {
		log.trace("reviewButtonTapped()")
		
		reviewRequested = true
	}
}
