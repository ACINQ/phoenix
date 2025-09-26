/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.components.buttons

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.TxId
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.utils.BlockchainExplorer

@Composable
fun WebLink(
    text: String,
    url: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = MaterialTheme.typography.body1.fontSize,
    iconSize: Dp = ButtonDefaults.IconSize,
    padding: PaddingValues = PaddingValues(horizontal = 2.dp, vertical = 1.dp),
    space: Dp = 8.dp,
    maxLines: Int = Int.MAX_VALUE,
    onClickLabel: String = stringResource(id = R.string.accessibility_link),
) {
    val context = LocalContext.current
    InlineButton(
        text = text,
        icon = R.drawable.ic_external_link,
        fontSize = fontSize,
        iconSize = iconSize,
        padding = padding,
        space = space,
        maxLines = maxLines,
        onClickLabel = onClickLabel,
        onClick = { openLink(context, url) },
        onLongClick = { copyToClipboard(context, text) },
        modifier = modifier,
    )
}

@Composable
fun AddressLinkButton(
    modifier: Modifier = Modifier,
    address: String,
) {
    addressUrl(address = address)?.let {
        WebLink(
            text = address,
            url = it,
            space = 4.dp,
            maxLines = 1,
            fontSize = 15.sp,
            iconSize = 14.dp,
            onClickLabel = stringResource(id = R.string.accessibility_address_explorer_link),
            modifier = modifier,
        )
    }
}

@Composable
fun InlineTransactionLink(
    modifier: Modifier = Modifier,
    txId: TxId,
) {
    txUrl(txId = txId)?.let {
        WebLink(
            text = txId.toString(),
            url = it,
            space = 4.dp,
            maxLines = 1,
            fontSize = 15.sp,
            iconSize = 14.dp,
            onClickLabel = stringResource(id = R.string.accessibility_explorer_link),
            modifier = modifier,
        )
    }
}

@Composable
fun txUrl(txId: TxId): String? {
    return LocalBusiness.current?.blockchainExplorer?.txUrl(txId = txId, website = BlockchainExplorer.Website.MempoolSpace)
}

@Composable
fun addressUrl(address: String): String? {
    return LocalBusiness.current?.blockchainExplorer?.addressUrl(addr = address, website = BlockchainExplorer.Website.MempoolSpace)
}

fun openLink(context: Context, link: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
}