import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ResetWalletView_Confirm"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ResetWalletView_Confirm: MVISubView, ViewName {
	
	@ObservedObject var mvi: MVIState<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
	
	let deleteTransactionHistory: Bool
	let deleteSeedBackup: Bool
	
	let syncSeedManager = Biz.syncManager!.syncSeedManager
	@State var backupSeed_state = SyncSeedManager_State.disabled
	@State var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	
	@State var confirm_taskDone: Bool = false
	@State var confirm_lossRisk: Bool = false
	@State var animatingConfirmToggleColor = false
	
	@State var didAppear = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			content()
		}
		.navigationTitle(NSLocalizedString("Confirm Reset", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			let showSteps = deleteTransactionHistory || deleteSeedBackup
			if showSteps {
				
				var idx: Int = 0
				let getIdx = { () -> Int in
					idx = idx + 1
					return idx
				}
				
				section_intro()
				if deleteTransactionHistory {
					section_transactionHistory(getIdx())
				}
				if deleteSeedBackup {
					section_seedBackup(getIdx())
				}
				section_localData(getIdx())
				
			} else {
				section_localData(nil)
			}
			
			if showICloudBackupWarning() {
				section_warning_iCloudSeedBackup()
			} else if showManualBackupWarning() {
				section_warning_manualSeedBackup()
			}
			section_button()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear {
			onAppear()
		}
		.onReceive(syncSeedManager.statePublisher) {
			syncStateChanged($0)
		}
	}
	
	@ViewBuilder
	func section_intro() -> some View {
		
		Section {
			Text("The following steps will be performed:")
				.listRowBackground(Color.clear)
				.listRowInsets(EdgeInsets(top: 0, leading: 15, bottom: 0, trailing: 15))
		}
	}
	
	@ViewBuilder
	func section_transactionHistory(_ idx: Int) -> some View {
		
		Section {
			Text("The **payment history** for this wallet will be deleted from your iCloud account.")
			
		} header: {
			Text("Step #\(idx)")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_seedBackup(_ idx: Int) -> some View {
		
		Section {
			Text("The **seed backup** for this wallet will be deleted from your iCloud account.")
			
		} header: {
			Text("Step #\(idx)")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_localData(_ idx: Int?) -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Text(
					"""
					The wallet will be completely deleted from **this device**.
					"""
				)
				
				Text(
					"""
					This will reset the app. \
					You will be taken to the Intro screen, and prompted to create or restore a wallet.
					"""
				)
				.font(.subheadline)
				.foregroundColor(.secondary)
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} header: {
			if let idx = idx {
				Text("Step #\(idx)")
			} else {
				EmptyView()
			}
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_warning_iCloudSeedBackup() -> some View {
		
		Section {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
					Text("iCloud backup not completed!")
						.bold()
					Text("You enabled iCloud backup for your recovery phrase, but the backup didn't complete.")
						.font(.subheadline)
				}
			} icon: {
				Image(systemName: "exclamationmark.circle")
					.renderingMode(.template)
					.imageScale(.large)
					.foregroundColor(Color.appNegative)
			}
			.frame(maxWidth: .infinity, alignment: Alignment.topLeading)
			.padding()
			.overlay(
				RoundedRectangle(cornerRadius: 10)
					.strokeBorder(Color.appNegative, lineWidth: 1)
			)
			.listRowBackground(Color.clear)
			.listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_warning_manualSeedBackup() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Label {
					VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
						Text("Don't lose your funds:")
							.bold()
						
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
					}
				} icon: {
					Image(systemName: "exclamationmark.circle")
						.renderingMode(.template)
						.imageScale(.large)
						.foregroundColor(.appNegative)
				}
				
				Label {
					Text("You are responsible for storing your recovery phrase.")
						.font(.subheadline)
						.foregroundColor(.secondary)
						.fixedSize(horizontal: false, vertical: true)
				} icon: {
					invisibleImage()
				}
				
				Toggle(isOn: $confirm_taskDone) {
					Text("I have saved my recovery phrase somewhere safe.")
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: checkboxOnImage(),
					offImage: checkboxOffImage()
				))
				
				Toggle(isOn: $confirm_lossRisk) {
					Text(
						"""
						I understand that if I lose my recovery phrase, \
						then I will lose the funds in my wallet.
						"""
					)
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: checkboxOnImage(),
					offImage: checkboxOffImage()
				))
				
			} // </VStack>
			.frame(maxWidth: .infinity, alignment: Alignment.topLeading)
			.padding()
			.overlay(
				RoundedRectangle(cornerRadius: 10)
					.strokeBorder(Color.appNegative, lineWidth: 1)
			)
			.listRowBackground(Color.clear)
			.listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				let isDisabled = buttonDisabled()
				Button {
					deleteWallet()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "exclamationmark.triangle")
							.imageScale(.medium)
						Text("Delete Wallet")
					}
					.font(.headline)
					.foregroundColor(isDisabled ? Color(UIColor.tertiaryLabel) : .appNegative)
				}
				.disabled(buttonDisabled())
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func checkboxOnImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.primary)
	}
	
	@ViewBuilder
	func checkboxOffImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
			.foregroundColor(animatingConfirmToggleColor ? Color.red : Color.primary)
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.clear)
			.accessibilityHidden(true)
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
	
	func formattedBalanceString() -> String {
		
		let (balance_bitcoin, balance_fiat) = formattedBalances()
		if let balance_fiat = balance_fiat {
			return "\(balance_bitcoin.string) (≈ \(balance_fiat.string))"
		} else {
			return balance_bitcoin.string
		}
	}
	
	func showICloudBackupWarning() -> Bool {
		
		// iCloud warning:
		//
		// > iCloud backup not completed!
		// > You enabled iCloud backup for your recovery phrase, but the backup didn't complete.
		
		if backupSeed_enabled {
			return backupSeed_state != .synced
		} else {
			return false
		}
	}
	
	func showManualBackupWarning() -> Bool {
		
		// Manual backup warning:
		//
		// > Don't lose your funds:
		// > You are responsible for storing your recovery phrase.
		// > ...
		
		if backupSeed_enabled {
			if deleteSeedBackup {
				// The seed is stored in iCloud, but the user is asking to delete their seed from iCloud!
				return mvi.balanceSats() > 0
			} else {
				// The seed is stored in iCloud, so the user can restore their wallet.
				return false
			}
		} else {
			// Maybe the user has reported that he/she has already backed up their seed.
			// But even if they did, this is still a perfect time to remind them.
			return mvi.balanceSats() > 0
		}
	}
	
	func buttonDisabled() -> Bool {
		
		if showICloudBackupWarning() {
		#if DEBUG
			return false
		#else
			return true
		#endif
		} else if showManualBackupWarning() {
			return !confirm_taskDone || !confirm_lossRisk
		} else {
			return false
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		guard !didAppear else {
			return
		}
		didAppear = true
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
			withAnimation(Animation.linear(duration: 1.0).repeatForever(autoreverses: true)) {
				animatingConfirmToggleColor = true
			}
		}
	}
	
	func syncStateChanged(_ newState: SyncSeedManager_State) {
		log.trace("syncStateChanged()")
		
		backupSeed_state = newState
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func deleteWallet() {
		log.trace("[\(viewName)] deleteWallet()")
		
		if let scene = UIApplication.shared.connectedScenes.first,
			let sceneDelegate = scene.delegate as? UIWindowSceneDelegate,
			let mySceneDelegate = sceneDelegate as? SceneDelegate
		{
			mySceneDelegate.transitionToResetWalletWindow(
				deleteTransactionHistory: deleteTransactionHistory,
				deleteSeedBackup: deleteSeedBackup
			)
		}
	}
}
