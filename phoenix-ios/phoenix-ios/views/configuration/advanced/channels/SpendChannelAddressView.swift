import SwiftUI
import PhoenixShared

fileprivate let filename = "SpendChannelAddressView"

#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SpendChannelAddressView: View {

	enum SpendChannelAddressState : Equatable {
		case idle
		case idle_fields_empty
		case processing
		case error(message: String)
		case success(pubkey: String, partialSignature: String, localNonce: String)
	}

	@State var state: SpendChannelAddressState = .idle
	var isProcessing: Bool {
		state == .processing
	}

	private var isProcessingOrSuccess: Bool {
		switch state {
		case .processing, .success:
			return true
		default:
			return false
		}
	}

	@State var amount: String = ""
	@State var txIndex: String = ""
	@State var channelId: String = ""
	@State var rawChannelData: String = ""
	@State var remoteFundingPubkey: String = ""
	@State var remoteNonce: String = ""
	@State var unsignedTx: String = ""

	@ViewBuilder
	var body: some View {
		content()
			.navigationTitle(NSLocalizedString("Spend from channel address", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.automatic)
	}

	@ViewBuilder
	func content() -> some View {
		ZStack(alignment: .top) {
			List {
				section_info()
				section_form()
				section_dynamic_bottom()
			}
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}

	@ViewBuilder
	func section_info() -> some View {
		Section() {
			Text("This screen is a debugging tool that helps recover funds that have been accidentally sent to a channel's outpoint.")
				.multilineTextAlignment(.leading)
		}
	}

	@ViewBuilder
	func section_form() -> some View {
		Section {
			TextInputField(label: "Amount", placeholder: "enter amount in satoshi", text: $amount, isDisabled: isProcessingOrSuccess, keyboardType: .numberPad)
			TextInputField(label: "Tx index", placeholder: "", text: $txIndex, isDisabled: isProcessingOrSuccess, keyboardType: .numberPad)
			TextInputField(label: "Channel id", placeholder: "", text: $channelId, isDisabled: isProcessingOrSuccess, layout: .vertical)
			TextInputField(label: "Raw channel data", placeholder: "", text: $rawChannelData, isDisabled: isProcessingOrSuccess, layout: .vertical)
			TextInputField(label: "Remote funding pubkey", placeholder: "", text: $remoteFundingPubkey, isDisabled: isProcessingOrSuccess, layout: .vertical)
			TextInputField(label: "Remote nonce", placeholder: "", text: $remoteNonce, isDisabled: isProcessingOrSuccess, layout: .vertical)
			TextInputField(label: "Unsigned tx", placeholder: "", text: $unsignedTx, isDisabled: isProcessingOrSuccess, layout: .vertical,)
		}
	}

	@ViewBuilder
	func section_dynamic_bottom() -> some View {
		switch state {
		case .success(let pubkey, let partialSignature, let localNonce):
			section_success(pubkey: pubkey, partialSignature: partialSignature, localNonce: localNonce)
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
				section_error(message: message)
			}
			
			section_confirm_button()
		}
	}

	@ViewBuilder
	private func section_confirm_button() -> some View {
		Section {
			Button {
				if isProcessing { return }
				if amount.trimmingCharacters(in: .whitespaces).isEmpty ||
					txIndex.trimmingCharacters(in: .whitespaces).isEmpty ||
					channelId.trimmingCharacters(in: .whitespaces).isEmpty ||
					rawChannelData.trimmingCharacters(in: .whitespaces).isEmpty ||
					remoteFundingPubkey.trimmingCharacters(in: .whitespaces).isEmpty ||
					remoteNonce.trimmingCharacters(in: .whitespaces).isEmpty ||
					unsignedTx.trimmingCharacters(in: .whitespaces).isEmpty {
					state = .idle_fields_empty
				} else {
					state = .processing
					Task.detached { await callSpendFromChannelAddress() }
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

	@ViewBuilder
	private func section_success(pubkey: String, partialSignature: String, localNonce: String) -> some View {
		Section {
			VStack(alignment: .leading, spacing: 5) {
				Text("Public key").fontWeight(.bold)
				Text(pubkey)
			}

			VStack(alignment: .leading, spacing: 5) {
				Text("Signature").fontWeight(.bold)
				Text(partialSignature)
			}

			VStack(alignment: .leading, spacing: 5) {
				Text("Nonce").fontWeight(.bold)
				Text(localNonce)
			}.padding(.bottom, 12)

			Button {
				UIPasteboard.general.string = """
				pubkey=\(pubkey)
				signature=\(partialSignature)
				nonce=\(localNonce)
				"""
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

	@ViewBuilder
	private func section_error(message: String) -> some View {
		Section {
			VStack(alignment: .center) {
				HStack(spacing: 5) {
					Image(systemName: "exclamationmark.triangle").imageScale(.medium)
					Text("An error occured")
				}
				Text(message).font(.callout).foregroundColor(.primary.opacity(0.5))
			}.frame(maxWidth: .infinity)
		}
	}

	private func callSpendFromChannelAddress() async {
		await Task.yield()
		if let amountValue = Int64(amount), let fundingTxIndexValue = Int64(txIndex) {
			do {
				let result = try await SpendChannelAddressHelper.shared.spendFromChannelAddress(
					business: Biz.business,
					amount: Bitcoin_kmpSatoshi(sat: amountValue),
					fundingTxIndex: fundingTxIndexValue,
					channelIdRaw: channelId,
					channelData: rawChannelData,
					remoteFundingPubkeyRaw: remoteFundingPubkey,
					unsignedTxRaw: unsignedTx,
					remoteNonceRaw: remoteNonce
				)

				await MainActor.run {
					switch result {
						
					case let success as SpendChannelAddressResult.Success:
						log.info("success: \(success.publicKey)")
						state = SpendChannelAddressState.success(pubkey: success.publicKey, partialSignature: success.signature, localNonce: success.localNonce)

					case let failure as SpendChannelAddressResult.FailureGeneric:
						log.error("error: \(failure.error.message ?? "generic")")
						state = SpendChannelAddressState.error(message: failure.error.message ?? "Unknown error.")

					case _ as SpendChannelAddressResult.FailureInvalidChannelId:
						log.error("error, invalid channel id")
						state = SpendChannelAddressState.error(message: "Invalid channel id.")

					case _ as SpendChannelAddressResult.FailureInvalidChannelBackup:
						log.error("error, invalid channel backup")
						state = SpendChannelAddressState.error(message: "Invalid raw channel data.")

					case let failure as SpendChannelAddressResult.FailureUnhandledChannelBackupVersion:
						log.error("error, unhandled backup version: \(failure.version)")
						state = SpendChannelAddressState.error(message: "Unhandled channel version (\(failure.version)).")

					case _ as SpendChannelAddressResult.FailureMissingChannelState:
						log.error("error, missing channel state")
						state = SpendChannelAddressState.error(message: "No match found for channel in raw channel data.")

					case let failure as SpendChannelAddressResult.FailureUnhandledChannelState:
						log.error("error, unhandled channel state: \(failure.state)")
						state = SpendChannelAddressState.error(message: "Unhandled channel state (\(failure.state).")

					case let failure as SpendChannelAddressResult.FailureTransactionMalformed:
						log.error("error, transaction is malformed: \(failure.details)")
						state = SpendChannelAddressState.error(message: "Unsigned transaction is invalid (\(failure.details)).")

					case _ as SpendChannelAddressResult.FailureRemoteNonceMalformed:
						log.error("error, remote nonce is malformed")
						state = SpendChannelAddressState.error(message: "Remote nonce is invalid.")

					case let failure as SpendChannelAddressResult.FailureRemoteFundingPubkeyMalformed:
						log.error("error, remote funding pubkey is malformed: \(failure.details)")
						state = SpendChannelAddressState.error(message: "Invalid remote funding pubkey (\(failure.details)).")

					case _ as SpendChannelAddressResult.FailureSigningFailure:
						log.error("error, signing has failed")
						state = SpendChannelAddressState.error(message: "Failed to sign transaction.")

					default:
						// limitation of KMP/SKIE, sealed class are converted to enums which means there's a default hypothetical
						log.info("error, result is unhandled")
						state = SpendChannelAddressState.idle
					}
				}

			} catch {
				await MainActor.run {
					log.error("uncaught error in spend-channel-helper:")
					state = SpendChannelAddressState.error(message: "Unknown error.")
				}
			}
		} else {
			await MainActor.run {
				state = SpendChannelAddressState.error(message: "Amount and tx index must be numbers.")
			}
		}
	}

	struct TextInputField: View {
		let label: String
		let placeholder: String
		@Binding var text: String
		var isDisabled: Bool

		enum LayoutStyle {
			case horizontal
			case vertical
		}
		var layout: LayoutStyle = .horizontal
		var keyboardType: UIKeyboardType = .alphabet
		
		var body: some View {

			let layout = layout == .horizontal
				? AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: 16))
				: AnyLayout(VStackLayout(alignment: .leading, spacing: 8))

			layout {
				Text(label)
					.font(.subheadline)

				HStack {
					TextField(placeholder, text: $text)
						.keyboardType(keyboardType)
						.disableAutocorrection(true)
						.autocapitalization(.none)
						.disabled(isDisabled)
				}
				.padding([.top, .bottom], 8)
				.padding(.leading, 12)
				.padding(.trailing, 8)
				.background(
					RoundedRectangle(cornerRadius: 12)
						.fill(Color.textFieldBorder.opacity(0.1))
				)
				.overlay(
					RoundedRectangle(cornerRadius: 12)
						.stroke(Color.textFieldBorder.opacity(0.5), lineWidth: 1)
				)
			}.frame(maxWidth: .infinity)
		}
	}
}
