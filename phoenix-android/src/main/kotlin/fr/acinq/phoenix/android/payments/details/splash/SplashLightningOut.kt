/*
 * Copyright 2024 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.payments.details.splash

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.WebLink
import fr.acinq.phoenix.android.components.contact.ContactOrOfferView
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.smartDescription
import fr.acinq.phoenix.data.LnurlPayMetadata
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import fr.acinq.phoenix.utils.extensions.state
import io.ktor.http.Url
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Composable
fun SplashLightningOutgoing(
    payment: LightningOutgoingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    metadata.lnurl?.let { lnurlMeta ->
        LnurlPayInfoView(payment, lnurlMeta)
    }

    payment.outgoingInvoiceRequest()?.payerNote?.takeIf { it.isNotBlank() }?.let {
        OfferPayerNote(payerNote = it)
    }

    SplashDescription(
        description = payment.smartDescription(),
        userDescription = metadata.userDescription,
        paymentId = payment.walletPaymentId(),
        onMetadataDescriptionUpdate = onMetadataDescriptionUpdate
    )
    SplashDestination(payment, metadata)
    SplashFee(payment = payment)

    (payment.status as? LightningOutgoingPayment.Status.Completed.Failed)?.let { status ->
        PaymentErrorView(status = status, failedParts = payment.parts.map { it.status }.filterIsInstance<LightningOutgoingPayment.Part.Status.Failed>())
    }
}

@Composable
private fun SplashDestination(payment: LightningOutgoingPayment, metadata: WalletPaymentMetadata) {
    val lnId = metadata.lnurl?.pay?.metadata?.lnid?.takeIf { it.isNotBlank() }
    if (lnId != null) {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label), icon = R.drawable.ic_zap) {
            SelectionContainer {
                Text(text = lnId)
            }
        }
    }
    val details = payment.details
    if (details is LightningOutgoingPayment.Details.Blinded) {
        val offer = details.paymentRequest.invoiceRequest.offer
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label)) {
            ContactOrOfferView(offer = offer)
        }
    }
}

@Composable
private fun SplashFee(payment: LightningOutgoingPayment) {
    val btcUnit = LocalBitcoinUnit.current
    if (payment.state() == WalletPaymentState.SuccessOffChain) {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
            Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
    }
}

@Composable
private fun LnurlPayInfoView(payment: LightningOutgoingPayment, metadata: LnurlPayMetadata) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_service)) {
        SelectionContainer {
            Text(text = metadata.pay.callback.host)
        }
    }
    metadata.successAction?.let {
        LnurlSuccessAction(payment = payment, action = it)
    }
}

@Composable
private fun LnurlSuccessAction(payment: LightningOutgoingPayment, action: LnurlPay.Invoice.SuccessAction) {
    Spacer(modifier = Modifier.height(8.dp))
    when (action) {
        is LnurlPay.Invoice.SuccessAction.Message -> {
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_message_label)) {
                SelectionContainer {
                    Text(text = action.message)
                }
            }
        }
        is LnurlPay.Invoice.SuccessAction.Url -> {
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_url_label)) {
                Text(text = action.description)
                WebLink(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_url_button), url = action.url.toString())
            }
        }
        is LnurlPay.Invoice.SuccessAction.Aes -> {
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_aes_label)) {
                val status = payment.status
                if (status is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain) {
                    val deciphered by produceState<String?>(initialValue = null) {
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(status.preimage.toByteArray(), "AES"), IvParameterSpec(action.iv.toByteArray()))
                        value = String(cipher.doFinal(action.ciphertext.toByteArray()), Charsets.UTF_8)
                    }
                    Text(text = action.description)
                    when (deciphered) {
                        null -> ProgressView(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_aes_decrypting), padding = PaddingValues(0.dp))
                        else -> {
                            val url = try {
                                Url(deciphered!!)
                            } catch (e: Exception) {
                                null
                            }
                            if (url != null) {
                                WebLink(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_url_button), url = url.toString())
                            } else {
                                SelectionContainer {
                                    Text(text = deciphered!!)
                                }
                            }
                        }
                    }
                } else {
                    Text(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_aes_decrypting))
                }
            }
        }
    }
}

@Composable
private fun PaymentErrorView(status: LightningOutgoingPayment.Status.Completed.Failed, failedParts: List<LightningOutgoingPayment.Part.Status.Failed>) {
    val failure = remember(status, failedParts) { OutgoingPaymentFailure(status.reason, failedParts) }
    translatePaymentError(failure).let {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_error_label)) {
            Text(text = it)
        }
    }
}

@Composable
fun translatePaymentError(paymentFailure: OutgoingPaymentFailure): String {
    val context = LocalContext.current
    val errorMessage = remember(key1 = paymentFailure) {
        when (val result = paymentFailure.explain()) {
            is Either.Left -> {
                when (val partFailure = result.value) {
                    is LightningOutgoingPayment.Part.Status.Failure.Uninterpretable -> partFailure.message
                    LightningOutgoingPayment.Part.Status.Failure.ChannelIsClosing -> context.getString(R.string.outgoing_failuremessage_channel_closing)
                    LightningOutgoingPayment.Part.Status.Failure.ChannelIsSplicing -> context.getString(R.string.outgoing_failuremessage_channel_splicing)
                    LightningOutgoingPayment.Part.Status.Failure.NotEnoughFees -> context.getString(R.string.outgoing_failuremessage_not_enough_fee)
                    LightningOutgoingPayment.Part.Status.Failure.NotEnoughFunds -> context.getString(R.string.outgoing_failuremessage_not_enough_balance)
                    LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooBig -> context.getString(R.string.outgoing_failuremessage_too_big)
                    LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooSmall -> context.getString(R.string.outgoing_failuremessage_too_small)
                    LightningOutgoingPayment.Part.Status.Failure.PaymentExpiryTooBig -> context.getString(R.string.outgoing_failuremessage_expiry_too_big)
                    LightningOutgoingPayment.Part.Status.Failure.RecipientRejectedPayment -> context.getString(R.string.outgoing_failuremessage_rejected_by_recipient)
                    LightningOutgoingPayment.Part.Status.Failure.RecipientIsOffline -> context.getString(R.string.outgoing_failuremessage_recipient_offline)
                    LightningOutgoingPayment.Part.Status.Failure.RecipientLiquidityIssue -> context.getString(R.string.outgoing_failuremessage_not_enough_liquidity)
                    LightningOutgoingPayment.Part.Status.Failure.TemporaryRemoteFailure -> context.getString(R.string.outgoing_failuremessage_temporary_failure)
                    LightningOutgoingPayment.Part.Status.Failure.TooManyPendingPayments -> context.getString(R.string.outgoing_failuremessage_too_many_pending)
                }
            }
            is Either.Right -> {
                when (result.value) {
                    FinalFailure.InvalidPaymentId -> context.getString(R.string.outgoing_failuremessage_invalid_id)
                    FinalFailure.AlreadyPaid -> context.getString(R.string.outgoing_failuremessage_alreadypaid)
                    FinalFailure.ChannelClosing -> context.getString(R.string.outgoing_failuremessage_channel_closing)
                    FinalFailure.ChannelNotConnected -> context.getString(R.string.outgoing_failuremessage_not_connected)
                    FinalFailure.ChannelOpening -> context.getString(R.string.outgoing_failuremessage_channel_opening)
                    FinalFailure.FeaturesNotSupported -> context.getString(R.string.outgoing_failuremessage_unsupported_features)
                    FinalFailure.InsufficientBalance -> context.getString(R.string.outgoing_failuremessage_not_enough_balance)
                    FinalFailure.InvalidPaymentAmount -> context.getString(R.string.outgoing_failuremessage_invalid_amount)
                    FinalFailure.NoAvailableChannels -> context.getString(R.string.outgoing_failuremessage_no_available_channels)
                    FinalFailure.RecipientUnreachable -> context.getString(R.string.outgoing_failuremessage_noroutefound)
                    FinalFailure.RetryExhausted -> context.getString(R.string.outgoing_failuremessage_noroutefound)
                    FinalFailure.UnknownError -> context.getString(R.string.outgoing_failuremessage_unknown)
                    FinalFailure.WalletRestarted -> context.getString(R.string.outgoing_failuremessage_restarted)
                }
            }
        }
    }
    return errorMessage
}