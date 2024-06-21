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

package fr.acinq.phoenix.android.payments.receive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.share

@Composable
fun OfferView(
    state: OfferState,
    maxWidth: Dp,
) {
    val context = LocalContext.current

    InvoiceHeader(icon = R.drawable.ic_zap, helpMessage = stringResource(id = R.string.receive_offer_help)) {
        Text(text = stringResource(id = R.string.receive_offer_title))
    }

    when (state) {
        OfferState.Init -> {
            Text(text = stringResource(id = R.string.utils_loading_data))
        }
        is OfferState.Show -> {
            QRCodeView(data = state.encoded, bitmap = state.bitmap, maxWidth = maxWidth)
            Spacer(modifier = Modifier.height(32.dp))
            CopyShareEditButtons(
                onCopy = { copyToClipboard(context, data = state.encoded) },
                onShare = { share(context, "lightning:${state.encoded}", context.getString(R.string.receive_offer_share_subject), context.getString(R.string.receive_offer_share_title)) },
                onEdit = null,
                maxWidth = maxWidth,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeDetail(label = stringResource(id = R.string.receive_offer_invoice_label), value = state.encoded, maxLines = 2)
            Spacer(modifier = Modifier.height(16.dp))
            TorWarning()
            HowToOffer()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorWarning() {
    val isTorEnabled by userPrefs.getIsTorEnabled.collectAsState(initial = null)

    if (isTorEnabled == true) {

        var showTorWarningDialog by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        Clickable(onClick = { showTorWarningDialog = true }, shape = RoundedCornerShape(12.dp)) {
            WarningMessage(
                header = stringResource(id = R.string.receive_tor_warning_title),
                details = null,
                alignment = Alignment.CenterHorizontally,
            )
        }

        if (showTorWarningDialog) {
            ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = {
                    // executed when user click outside the sheet, and after sheet has been hidden thru state.
                    showTorWarningDialog = false
                },
                containerColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface,
                scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = 200.dp)
                        .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 90.dp)
                ) {
                    Text(text = stringResource(id = R.string.receive_tor_warning_title), style = MaterialTheme.typography.h4)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(id = R.string.receive_tor_warning_dialog_content_1))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(id = R.string.receive_tor_warning_dialog_content_2))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowToOffer() {
    var showHowToDialog by remember { mutableStateOf(false) }

    Clickable(onClick = { showHowToDialog = true }, shape = RoundedCornerShape(12.dp)) {
        InfoMessage(
            header = stringResource(id = R.string.receive_offer_howto_title),
            alignment = Alignment.CenterHorizontally,
        )
    }

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    if (showHowToDialog) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                // executed when user click outside the sheet, and after sheet has been hidden thru state.
                showHowToDialog = false
            },
            modifier = Modifier.heightIn(max = 700.dp),
            containerColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
            scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 90.dp)
            ) {
                Text(text = stringResource(id = R.string.receive_offer_title), style = MaterialTheme.typography.h4)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_1))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_2))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_3))
                Spacer(modifier = Modifier.height(32.dp))
                BorderButton(
                    text = stringResource(id = R.string.receive_offer_howto_details_faq_link),
                    icon = R.drawable.ic_help_circle,
                    onClick = { openLink(context, "https://phoenix.acinq.co/faq") },
                    modifier = Modifier.align(Alignment.End)
                )
            }

        }
    }
}