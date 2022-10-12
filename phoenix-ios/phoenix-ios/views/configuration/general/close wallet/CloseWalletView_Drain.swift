import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloseWalletView_Drain"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct CloseWalletView_Drain: MVISubView {
	
	@ObservedObject var mvi: MVIState<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
	let bitcoinAddress: String
	let popToRoot: () -> Void
	
	@State var actionRequested: Bool = false
	@State var expectedTxCount: Int = 0
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			NavigationLink(
				destination: actionScreen(),
				isActive: $actionRequested
			) {
				EmptyView()
			}
			.accessibilityHidden(true)
			
			content()
		}
		.navigationTitle(NSLocalizedString("Confirm Close", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_notices()
			section_button()
		}
		.listStyle(.insetGrouped)
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		let balance_info = formattedBalanceString()
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Text("Your account balance of:")
					.padding(.bottom, 5)
				
				Text(verbatim: balance_info)
					.font(.body.weight(.semibold))
					.padding(.bottom, 15)
				
				Text("will be sent to:")
					.padding(.bottom, 5)
				
				Text(verbatim: bitcoinAddress)
					.font(.system(.callout, design: .monospaced).weight(.semibold))
				
			} // </VStack>
			.multilineTextAlignment(.center)
			.padding(.vertical, 5)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_notices() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					Text("Please ensure the address is correct")
				} icon: {
					Image(systemName: "eye")
				}
				.font(.callout)
				.padding(.bottom, 15)
				
				Label {
					Text("The transaction may take 30 minutes or more to confirm on the bitcoin blockchain")
				} icon: {
					Image(systemName: "tortoise")
				}
				.font(.callout)
				.foregroundColor(.secondary)
				
			} // </VStack>
			.padding(.vertical, 5)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					drainWallet()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "bitcoinsign.circle")
							.imageScale(.medium)
						Text("Drain my wallet")
					}
					.font(.headline)
					.foregroundColor(.appNegative)
				}
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func actionScreen() -> some View {
		
		CloseWalletView_Drain_Action(
			mvi: mvi,
			expectedTxCount: expectedTxCount,
			popToRoot: popToRoot
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
	
	func formattedBalanceString() -> String {
		
		let (balance_bitcoin, balance_fiat) = formattedBalances()
		if let balance_fiat = balance_fiat {
			return "\(balance_bitcoin.string) (≈ \(balance_fiat.string))"
		} else {
			return balance_bitcoin.string
		}
	}
	
	func nonZeroChannelCount() -> Int {
		
		if let channels = mvi.channels() {
			return channels.filter { $0.balance > 0 }.count
		} else {
			return 0
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func drainWallet() {
		log.trace("drainWallet()")
		
		actionRequested = true
		expectedTxCount = nonZeroChannelCount()
		mvi.intent(CloseChannelsConfiguration.IntentMutualCloseAllChannels(address: bitcoinAddress))
	}
}
