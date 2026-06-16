import SwiftUI
import PhoenixShared

fileprivate let filename = "SwapInSignerView"

#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SwapInSignerView: View {

	enum SwapInOption: String, CaseIterable, Identifiable {
		case legacy
		case taproot

		var id: Self { self }

		var title: String {
			switch self {
			case .legacy:
				return "Legacy"
			case .taproot:
				return "Taproot"
			}
		}
	}

	enum SwapInSignerState : Equatable {
		case idle
		case idle_fields_empty
		case processing
		case error(message: String)
		case legacy_success(txId: String, userPubkey: String, userSignature: String)
		case taproot_success(txId: String, userPubkey: String, userSignature: String, userRefundKey: String, userNonce: String)
	}

	@State var state: SwapInSignerState = .idle
	var isProcessing: Bool {
		state == .processing
	}

	private var isProcessingOrSuccess: Bool {
		switch state {
		case .processing, .legacy_success, .taproot_success:
			return true
		default:
			return false
		}
	}

	@State private var selectedOption: SwapInOption = .legacy
	@State private var legacyUnsignedTx: String = ""
	@State private var taprootUnsignedTx: String = ""
	@State private var taprootServerNonce: String = ""

	@ViewBuilder
	var body: some View {
		content()
			.navigationTitle(NSLocalizedString("Swap-in signer", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.automatic)
	}

	@ViewBuilder
	func content() -> some View {
		ZStack(alignment: .top) {
			List {
				section_info_config()
				switch selectedOption {
				case .legacy:
					section_legacy_form()
				case .taproot:
					section_taproot_form()
				}
				section_confirm_button()
			}
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}

	@ViewBuilder
	func section_info_config() -> some View {
		Section() {
			Text("This debugging tool lets you sign swap-in inputs. Only use if you understand what it does.")
				.multilineTextAlignment(.leading)
			VStack(spacing: 0) {
				ForEach(SwapInOption.allCases) { option in
					Button {
						selectedOption = option
					} label: {
						RadioRow(
							title: option.title,
							isSelected: selectedOption == option
						)
					}
					.buttonStyle(.plain)
				}
			}
		}
	}

	@ViewBuilder
	private func section_legacy_form() -> some View {
		Section() {
			TextInputField(label: "Unsigned tx", placeholder: "", text: $legacyUnsignedTx, isDisabled: isProcessingOrSuccess, layout: .vertical,)
		}
	}

	@ViewBuilder
	private func section_taproot_form() -> some View {
		Section() {
			TextInputField(label: "Unsigned tx", placeholder: "", text: $taprootUnsignedTx, isDisabled: isProcessingOrSuccess, layout: .vertical,)
			TextInputField(label: "Server nonce", placeholder: "", text: $taprootServerNonce, isDisabled: isProcessingOrSuccess, layout: .vertical,)
		}
	}

	@ViewBuilder
	private func section_confirm_button() -> some View {

		switch state {
		case .legacy_success(let txId, let userPubkey, let userSignature):
			section_success(txId: txId, userPubkey: userPubkey, userSignature: userSignature, userRefundKey: nil, userNonce: nil)
		case .taproot_success(let txId, let userPubkey, let userSignature, let userRefundKey, let userNonce):
			section_success(txId: txId, userPubkey: userPubkey, userSignature: userSignature, userRefundKey: userRefundKey, userNonce: userNonce)
		case .processing:
			Section {
				Text("Signing...").frame(maxWidth: .infinity, alignment: .center)
			}
		default:
			if case .idle_fields_empty = state {
				Section {
					Text("Please fill all required fields").frame(maxWidth: .infinity, alignment: .center)
				}
			}

			if case .error(let message) = state {
				Section {
					VStack(alignment: .center) {
						HStack(spacing: 5) {
							Image(systemName: "exclamationmark.triangle").imageScale(.medium)
							Text("An error has occurred.")
						}
						Text(message).font(.callout).foregroundColor(.primary.opacity(0.5))
					}.frame(maxWidth: .infinity)
				}
			}

			// confirm button
			Section {
				Button {
					if isProcessing { return }
					switch selectedOption {
					case .legacy:
						if legacyUnsignedTx.trimmingCharacters(in: .whitespaces).isEmpty {
							state = .idle_fields_empty
						} else {
							state = .processing
							Task.detached { await callSwapInSignLegacy() }
						}
					case .taproot:
						if taprootUnsignedTx.trimmingCharacters(in: .whitespaces).isEmpty ||
							taprootServerNonce.trimmingCharacters(in: .whitespaces).isEmpty {
							state = .idle_fields_empty
						} else {
							state = .processing
							Task.detached { await callSwapInSignTaproot() }
						}
					}
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "pencil.and.scribble").imageScale(.medium)
						Text("Sign")
					}
				}
				.disabled(isProcessing)
				.frame(maxWidth: .infinity)
			}
		}
	}

	@ViewBuilder
	private func section_success(txId: String, userPubkey: String, userSignature: String, userRefundKey: String?, userNonce: String?) -> some View {
		Section {
			VStack(alignment: .leading, spacing: 5) {
				Text("Tx id").fontWeight(.bold)
				Text(txId)
			}

			VStack(alignment: .leading, spacing: 5) {
				Text("Public key").fontWeight(.bold)
				Text(userPubkey)
			}

			VStack(alignment: .leading, spacing: 5) {
				Text("Signature").fontWeight(.bold)
				Text(userSignature)
			}

			if let userRefundKey {
				VStack(alignment: .leading, spacing: 5) {
					Text("Refund key").fontWeight(.bold)
					Text(userRefundKey)
				}
			}

			if let userNonce {
				VStack(alignment: .leading, spacing: 5) {
					Text("Nonce").fontWeight(.bold)
					Text(userNonce)
				}
			}

			let concat_data = {
				var parts = ["tx_id=\(txId)", "user_pubkey=\(userPubkey)", "user_signature=\(userSignature)"]
				if let userRefundKey { parts.append("user_refund_key=\(userRefundKey)") }
				if let userNonce { parts.append("user_nonce=\(userNonce)") }
				return parts.joined(separator: "\n")
			}()

			Button {
				UIPasteboard.general.string = concat_data
			} label: {
				HStack(spacing: 5) {
					Image(systemName: "square.on.square").imageScale(.medium)
					Text("Copy data")
				}
			}
			.padding(4)
			.frame(maxWidth: .infinity)
		}
	}

	private func callSwapInSignLegacy() async {
		await Task.yield()

		do {
			log.info("(legacy) signing swap-in tx: \(legacyUnsignedTx)")
			let result = try await SwapInSignerHelper.shared.signPartialTxLegacy(
				business: Biz.business,
				unsignedTx: legacyUnsignedTx,
			)

			await MainActor.run {
				switch result {

				case let success as LegacySwapInSignerResult.Success:
					state = SwapInSignerState.legacy_success(txId: success.txId, userPubkey: success.userPubkey, userSignature: success.userSignature)

				case _ as LegacySwapInSignerResult.FailureTransactionMalformed:
					state = SwapInSignerState.error(message: "Invalid transaction input")

				case let failure as LegacySwapInSignerResult.FailureTooManyInputs:
					state = SwapInSignerState.error(message: "Transaction has too many inputs (\(failure.inputSize))")

				case _ as LegacySwapInSignerResult.FailureParentTxNotFound:
					state = SwapInSignerState.error(message: "Parent transaction not found")

				case _ as LegacySwapInSignerResult.FailureSigningError:
					state = SwapInSignerState.error(message: "Signing error")

				default:
					log.info("(legacy) unhandled result=\(result)")
					state = SwapInSignerState.idle
				}
			}
		} catch {
			await MainActor.run {
				log.error("(legacy) uncaught error in swap-in-signer")
				state = SwapInSignerState.error(message: "Unknown error.")
			}
		}
	}

	private func callSwapInSignTaproot() async {
		await Task.yield()

		do {
			log.info("(taproot) signing swap-in tx: \(taprootUnsignedTx) nonce: \(taprootServerNonce)")
			let result = try await SwapInSignerHelper.shared.signPartialTxTaproot(
				business: Biz.business,
				unsignedTx: taprootUnsignedTx,
				serverNonce: taprootServerNonce
			)

			await MainActor.run {
				switch result {

				case let success as TaprootSwapInSignerResult.Success:
					state = SwapInSignerState.taproot_success(txId: success.txId, userPubkey: success.userPubkey, userSignature: success.userSignature, userRefundKey: success.userRefundKey, userNonce: success.userNonce)

				case _ as TaprootSwapInSignerResult.FailureTransactionMalformed:
					state = SwapInSignerState.error(message: "Invalid transaction input")

				case _ as TaprootSwapInSignerResult.FailureAddressIndexNotFound:
					state = SwapInSignerState.error(message: "Address index not found")

				case _ as TaprootSwapInSignerResult.FailureAddressIndexNotFound:
					state = SwapInSignerState.error(message: "Nonce generation failure")

				case let failure as TaprootSwapInSignerResult.FailureTooManyInputs:
					state = SwapInSignerState.error(message: "Transaction has too many inputs (\(failure.inputSize))")

				case _ as TaprootSwapInSignerResult.FailureParentTxNotFound:
					state = SwapInSignerState.error(message: "Parent transaction not found")

				case _ as TaprootSwapInSignerResult.FailureSwapProtocolError:
					state = SwapInSignerState.error(message: "Swap-in protocol details could not be retrieved")

				case _ as TaprootSwapInSignerResult.FailureSigningError:
					state = SwapInSignerState.error(message: "Failed to sign data")

				default:
					log.info("(taproot) unhandled result=\(result)")
					state = SwapInSignerState.idle
				}
			}
		} catch {
			await MainActor.run {
				log.error("(taproot) uncaught error in swap-in-signer:")
				state = SwapInSignerState.error(message: "Unknown error.")
			}
		}
	}
}

struct RadioRow: View {
	let title: String
	let isSelected: Bool

	var body: some View {
		HStack(spacing: 16) {
			Image(systemName:
				isSelected
				? "largecircle.fill.circle"
				: "circle"
			)
			.foregroundStyle(.primary)
			Text(title)
			Spacer()
		}
		.frame(maxWidth: .infinity)
		.frame(height: 56)
		.padding(.horizontal, 16)
		.contentShape(Rectangle())
	}
}
