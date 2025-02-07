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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.internalData
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.share


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOfferDialog(
    onDismiss: () -> Unit,
    offerState: OfferState,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bip353AddressState = internalData.getBip353Address.collectAsState(initial = null)
    val address = bip353AddressState.value

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
            // executed when user click outside the sheet, and after sheet has been hidden thru state.
            onDismiss()
        },
        modifier = Modifier.heightIn(max = 700.dp),
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 0.dp, start = 12.dp, end = 12.dp, bottom = 50.dp)
        ) {
            when (offerState) {
                OfferState.Init -> {
                    Text(text = stringResource(id = R.string.utils_loading_data))
                }
                is OfferState.Show -> {
                    HowToOffer()
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier
                        .widthIn(max = 350.dp)
                        .clip(shape = RoundedCornerShape(16.dp))) {
                        QRCodeImage(bitmap = offerState.bitmap, onLongClick = { copyToClipboard(context, offerState.encoded) })
                    }
                    Bip353AddressDisplay(address)
                    Row {
                        var showCopyDialog by remember { mutableStateOf(false) }
                        if (showCopyDialog && !address.isNullOrBlank()) {
                            CopyAddressDialog(address = address, offer = offerState.encoded, onDismiss = { showCopyDialog = false })
                        }
                        BorderButton(
                            text = stringResource(id = R.string.btn_copy),
                            icon = R.drawable.ic_copy,
                            onClick = { if (!address.isNullOrBlank()) showCopyDialog = true else copyToClipboard(context, data = offerState.encoded) },
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        BorderButton(
                            text = stringResource(id = R.string.btn_share),
                            icon = R.drawable.ic_share,
                            onClick = { share(context, "bitcoin:?lno=${offerState.encoded}", context.getString(R.string.receive_offer_share_subject), context.getString(R.string.receive_offer_share_title)) },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TorWarning()
                }
            }
        }
    }
}

@Composable
fun Bip353AddressDisplay(address: String?) {
    Spacer(modifier = Modifier.height(12.dp))
    when {
        address.isNullOrBlank() -> {}
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colors.primary, CircleShape)
                        .padding(3.dp)
                ) {
                    PhoenixIcon(
                        resourceId = R.drawable.ic_bitcoin_wire,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colors.primary,
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                SelectionContainer {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyAddressDialog(
    address: String,
    offer: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
            // executed when user click outside the sheet, and after sheet has been hidden thru state.
            onDismiss()
        },
        modifier = Modifier.heightIn(max = 700.dp),
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
    ) {
        Column(Modifier.padding(bottom = 50.dp)) {
            CopyButtonDialog(label = stringResource(id = R.string.receive_offer_copy_bip353), valueToCopy = stringResource(id = R.string.utils_bip353_with_prefix, address))
            CopyButtonDialog(label = stringResource(id = R.string.receive_offer_copy_bolt12), valueToCopy = offer)
            CopyButtonDialog(label = stringResource(id = R.string.receive_offer_copy_bip21), valueToCopy = "bitcoin:?lno=$offer")
        }
    }
}

@Composable
private fun CopyButtonDialog(label: String, valueToCopy: String) {
    val context = LocalContext.current
    Clickable(onClick = { copyToClipboard(context, data = valueToCopy) }, modifier = Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            TextWithIcon(text = label, textStyle = MaterialTheme.typography.body2, icon = R.drawable.ic_copy)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = valueToCopy, style = MaterialTheme.typography.caption.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToOffer() {
    var showHowToDialog by remember { mutableStateOf(false) }

    Clickable(onClick = { showHowToDialog = true }, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(id = R.string.receive_offer_howto_intro_1), textAlign = TextAlign.Center, style = MaterialTheme.typography.body2)
            Spacer(modifier = Modifier.height(4.dp))
            TextWithIcon(text = stringResource(id = R.string.receive_offer_howto_intro_2), icon = R.drawable.ic_info)
        }
    }

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 50.dp),
            ) {
                Text(text = stringResource(id = R.string.receive_offer_howto_details_1), style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_2))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_3))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_4_title), style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_4))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_5_title), style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.receive_offer_howto_details_5))
                Spacer(modifier = Modifier.height(24.dp))
                BorderButton(
                    text = stringResource(id = R.string.receive_offer_howto_details_faq_link),
                    icon = R.drawable.ic_external_link,
                    onClick = { openLink(context, "https://bolt12.org") },
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
