import SwiftUI
import PhoenixShared

fileprivate let filename = "FinalWalletDetails"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct FinalWalletDetails: View {
	
	enum NavLinkTag: String {
		case SpendFinalWalletFunds
	}
	
	@State var finalWallet = Biz.business.peerManager.finalWalletValue()
	let finalWalletPublisher = Biz.business.peerManager.finalWalletPublisher()
	
	@State var blockchainExplorerTxid: Bitcoin_kmpTxId? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Final wallet", comment: "Navigation Bar Title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_confirmed()
			section_unconfirmed()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(finalWalletPublisher) {
			finalWalletChanged($0)
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			Text("The final wallet is where funds are sent by default when your Lightning channels are closed.")
		}
	}
	
	@ViewBuilder
	func section_confirmed() -> some View {
		
		Section {
			
			let confirmed = confirmedBalance()

			if hasConfirmedSats() {
				navLink_plain(.SpendFinalWalletFunds) {
					Text(verbatim: "\(confirmed.0.string)") +
					Text(verbatim: " ≈ \(confirmed.1.string)").foregroundColor(.secondary)
				}
			} else {
				Text(verbatim: "\(confirmed.0.string)") +
				Text(verbatim: " ≈ \(confirmed.1.string)").foregroundColor(.secondary)
			}
			
		} header: {
			Text("Confirmed Balance")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_unconfirmed() -> some View {
		
		Section {
			
			let utxos = unconfirmedUtxos()
			if utxos.isEmpty {
			
				Text("No pending transactions")
					.foregroundColor(.secondary)
				
			} else {
				
				ForEach(utxos) { utxo in
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						
						Text(utxo.txid.toHex())
							.font(.subheadline)
							.foregroundColor(.secondary)
							.lineLimit(1)
							.truncationMode(.tail)
							.padding(.trailing, 15)
						
						Group {
							let (btcAmt, fiatAmt) = formattedBalances(utxo.amount)
							
							Text(verbatim: "\(btcAmt.string) ") +
							Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
						}
						.padding(.trailing, 4)
						.layoutPriority(1)
						
						Spacer(minLength: 0)
						
						Button {
							blockchainExplorerTxid = utxo.txid
						} label: {
							Image(systemName: "link")
						}
					}
				}
			}
			
		} header: {
			Text("Unconfirmed Balance")
			
		} // </Section>
		.confirmationDialog("Blockchain Explorer",
			isPresented: confirmationDialogBinding(),
			titleVisibility: .automatic
		) {
			if let txid = blockchainExplorerTxid {
				Button {
					exploreTx(txid, website: BlockchainExplorer.WebsiteMempoolSpace())
				} label: {
					Text(verbatim: "Mempool.space") // no localization needed
						.textCase(.none)
				}
				Button {
					exploreTx(txid, website: BlockchainExplorer.WebsiteBlockstreamInfo())
				} label: {
					Text(verbatim: "Blockstream.info") // no localization needed
						.textCase(.none)
				}
				Button {
					copyTxId(txid)
				} label: {
					Text("Copy transaction id")
						.textCase(.none)
				}
			}
		} // </confirmationDialog>
	}
	
	@ViewBuilder
	func navLink_plain<Content>(
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
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .SpendFinalWalletFunds: SpendOnChainFunds(source: .finalWallet)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func hasConfirmedSats() -> Bool {
		return finalWallet.anyConfirmedBalance.sat > 0
	}
	
	func confirmedBalance() -> (FormattedAmount, FormattedAmount) {
		
		let confirmed = finalWallet.anyConfirmedBalance
		
		let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: confirmed)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: confirmed)
		
		return (btcAmt, fiatAmt)
	}
	
	func unconfirmedUtxos() -> [UtxoWrapper] {
		
		let utxos = finalWallet.unconfirmed
		let wrappedUtxos = utxos.map { utxo in
			
			let confirmationCount = (utxo.blockHeight == 0)
			  ? 0
			  : Int64(finalWallet.currentBlockHeight) - utxo.blockHeight + 1
			
			return UtxoWrapper(utxo: utxo, confirmationCount: confirmationCount)
		}
		
		return wrappedUtxos
	}
	
	func formattedBalances(_ sats: Bitcoin_kmpSatoshi) -> (FormattedAmount, FormattedAmount) {
		
		let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (btcAmt, fiatAmt)
	}
	
	func confirmationDialogBinding() -> Binding<Bool> {
		
		return Binding( // SwiftUI only allows for 1 ".sheet"
			get: { blockchainExplorerTxid != nil },
			set: { if !$0 { blockchainExplorerTxid = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func finalWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("finalWalletChanged()")
		
		finalWallet = newValue
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func exploreTx(_ txid: Bitcoin_kmpTxId, website: BlockchainExplorer.Website) {
		log.trace("exploreTX()")
		
		let txUrlStr = Biz.business.blockchainExplorer.txUrl(txId: txid, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	func copyTxId(_ txid: Bitcoin_kmpTxId) {
		log.trace("copyTxId()")
		
		UIPasteboard.general.string = txid.toHex()
	}
}
