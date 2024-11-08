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

package fr.acinq.phoenix.android.utils.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.managers.SendManager
import java.security.cert.CertificateException

fun Connection.CLOSED.isBadCertificate() = this.reason?.cause is CertificateException

@Composable
fun SendManager.ParseResult.BadRequest.toLocalisedMessage(): String {
    return when (val reason = this.reason) {
        is SendManager.BadRequestReason.Expired -> stringResource(R.string.send_error_invoice_expired)
        is SendManager.BadRequestReason.ChainMismatch -> stringResource(R.string.send_error_invalid_chain)
        is SendManager.BadRequestReason.AlreadyPaidInvoice -> stringResource(R.string.send_error_already_paid)
        is SendManager.BadRequestReason.ServiceError -> reason.error.toLocalisedMessage().text
        is SendManager.BadRequestReason.InvalidLnurl -> stringResource(R.string.send_error_lnurl_invalid)
        is SendManager.BadRequestReason.UnsupportedLnurl -> stringResource(R.string.send_error_lnurl_unsupported)
        is SendManager.BadRequestReason.UnknownFormat -> stringResource(R.string.send_error_invalid_generic)
        is SendManager.BadRequestReason.Bip353NameNotFound -> stringResource(id = R.string.send_error_bip353_name_not_found, reason.username, reason.domain)
        is SendManager.BadRequestReason.Bip353InvalidUri -> stringResource(id = R.string.send_error_bip353_invalid_uri)
        is SendManager.BadRequestReason.Bip353InvalidOffer -> stringResource(id = R.string.send_error_bip353_invalid_offer)
        is SendManager.BadRequestReason.Bip353NoDNSSEC -> stringResource(id = R.string.send_error_bip353_dnssec)
    }
}

@Composable
fun LnurlError.RemoteFailure.toLocalisedMessage(): AnnotatedString {
    return when (this) {
        is LnurlError.RemoteFailure.Code -> annotatedStringResource(id = R.string.lnurl_error_remote_code, origin, code.value.toString())
        is LnurlError.RemoteFailure.CouldNotConnect -> annotatedStringResource(id = R.string.lnurl_error_remote_connection, origin)
        is LnurlError.RemoteFailure.Detailed -> annotatedStringResource(id = R.string.lnurl_error_remote_details, origin, reason)
        is LnurlError.RemoteFailure.Unreadable -> annotatedStringResource(id = R.string.lnurl_error_remote_unreadable, origin)
        is LnurlError.RemoteFailure.IsWebsite -> annotatedStringResource(id = R.string.lnurl_error_remote_is_website)
        is LnurlError.RemoteFailure.LightningAddressError -> annotatedStringResource(id = R.string.lnurl_error_remote_unknown_lnaddress, origin)
    }
}