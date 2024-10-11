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
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.lnurl.LnurlError
import java.security.cert.CertificateException

fun Connection.CLOSED.isBadCertificate() = this.reason?.cause is CertificateException

@Composable
fun Scan.Model.BadRequest.toLocalisedMessage(): String {
    return when (val reason = this.reason) {
        is Scan.BadRequestReason.Expired -> stringResource(R.string.scan_error_expired)
        is Scan.BadRequestReason.ChainMismatch -> stringResource(R.string.scan_error_invalid_chain)
        is Scan.BadRequestReason.AlreadyPaidInvoice -> stringResource(R.string.scan_error_already_paid)
        is Scan.BadRequestReason.ServiceError -> reason.error.toLocalisedMessage().text
        is Scan.BadRequestReason.InvalidLnurl -> stringResource(R.string.scan_error_lnurl_invalid)
        is Scan.BadRequestReason.UnsupportedLnurl -> stringResource(R.string.scan_error_lnurl_unsupported)
        is Scan.BadRequestReason.UnknownFormat -> stringResource(R.string.scan_error_invalid_generic)
        is Scan.BadRequestReason.Bip353NameNotFound -> stringResource(id = R.string.scan_error_bip353_name_not_found, reason.username, reason.domain)
        is Scan.BadRequestReason.Bip353InvalidUri -> stringResource(id = R.string.scan_error_bip353_invalid_uri)
        is Scan.BadRequestReason.Bip353InvalidOffer -> stringResource(id = R.string.scan_error_bip353_invalid_offer)
        is Scan.BadRequestReason.Bip353NoDNSSEC -> stringResource(id = R.string.scan_error_bip353_dnssec)
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