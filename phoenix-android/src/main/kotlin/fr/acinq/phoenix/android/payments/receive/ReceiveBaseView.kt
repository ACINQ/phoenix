/*
 * Copyright 2020 ACINQ SAS
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.safeLet

@Composable
fun ReceiveView(
    onBackClick: () -> Unit,
    onSwapInReceived: () -> Unit,
    onFeeManagementClick: () -> Unit,
    onScanDataClick: () -> Unit,
) {
    val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory(business.chain, business.peerManager, business.walletManager))

    DefaultScreenLayout(horizontalAlignment = Alignment.CenterHorizontally, isScrollable = true) {
        DefaultScreenHeader(
            title = if (vm.isEditingLightningInvoice) {
                stringResource(id = R.string.receive_lightning_edit_title)
            } else null,
            onBackClick = {
                if (vm.isEditingLightningInvoice) {
                    vm.isEditingLightningInvoice = false
                } else {
                    onBackClick()
                }
            },
        )
        ReceiveViewPages(vm = vm, onFeeManagementClick = onFeeManagementClick, onScanDataClick = onScanDataClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiveViewPages(
    vm: ReceiveViewModel,
    onFeeManagementClick: () -> Unit,
    onScanDataClick: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    // we need to be responsive in some subcomponents, like the edit-invoice buttons
    BoxWithConstraints {
        HorizontalPager(
            modifier = Modifier.fillMaxHeight(),
            state = pagerState,
            contentPadding = PaddingValues(
                horizontal = when {
                    maxWidth <= 240.dp -> 30.dp
                    maxWidth <= 320.dp -> 40.dp
                    maxWidth <= 480.dp -> 44.dp
                    else -> 52.dp
                }
            ),
            verticalAlignment = Alignment.Top
        ) { index ->
            val maxWidth = maxWidth
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = when {
                            maxWidth <= 320.dp -> 6.dp
                            maxWidth <= 480.dp -> 8.dp
                            else -> 10.dp
                        },
                        vertical = when {
                            maxHeight <= 800.dp -> 32.dp
                            else -> 50.dp
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (index) {
                    0 -> {
                        val defaultInvoiceExpiry by userPrefs.getInvoiceDefaultExpiry.collectAsState(null)
                        val defaultInvoiceDesc by userPrefs.getInvoiceDefaultDesc.collectAsState(null)
                        safeLet(defaultInvoiceDesc, defaultInvoiceExpiry) { desc, expiry ->
                            LightningInvoiceView(vm = vm, onFeeManagementClick = onFeeManagementClick, onScanDataClick = onScanDataClick,
                                defaultDescription = desc, defaultExpiry = expiry, maxWidth = maxWidth, isPageActive = pagerState.currentPage == 0)
                        } ?: ProgressView(text = stringResource(id = R.string.utils_loading_prefs))
                    }
                    1 -> BitcoinAddressView(vm = vm, maxWidth = maxWidth)
                }
            }
        }
    }
}

@Composable
fun InvoiceHeader(
    icon: Int,
    helpMessage: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconPopup(
            icon = icon,
            iconSize = 24.dp,
            iconPadding = 4.dp,
            colorAtRest = MaterialTheme.colors.primary,
            spaceRight = 8.dp,
            spaceLeft = null,
            popupMessage = helpMessage
        )
        content()
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun QRCodeView(
    data: String?,
    bitmap: ImageBitmap?,
    details: @Composable () -> Unit = {},
    maxWidth: Dp,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(
                when {
                    maxWidth <= 240.dp -> 160.dp
                    maxWidth <= 320.dp -> 240.dp
                    maxWidth <= 480.dp -> 270.dp
                    else -> 320.dp
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = RoundedCornerShape(16.dp)
            )
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QRCodeImage(bitmap) { data?.let { copyToClipboard(context, it) } }
        details()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QRCodeImage(
    bitmap: ImageBitmap?,
    onLongClick: () -> Unit,
) {
    var showFullScreenQR by remember { mutableStateOf(false) }
    val image: @Composable () -> Unit = {
        if (bitmap == null) {
            Image(
                painter = painterResource(id = R.drawable.ic_white),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(id = R.string.receive_help_qr),
                alignment = Alignment.Center,
                contentScale = ContentScale.FillWidth,
            )
        }
    }
    Surface(
        Modifier
            .combinedClickable(
                role = Role.Button,
                onClick = { if (bitmap != null) showFullScreenQR = true },
                onLongClick = onLongClick,
            )
            .fillMaxWidth()
            .background(Color.White)
            .padding(24.dp)
    ) { image() }

    if (showFullScreenQR) {
        FullScreenDialog(onDismiss = { showFullScreenQR = false }) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) { image() }
        }
    }
}

@Composable
fun QRCodeDetail(label: String, value: String, maxLines: Int = Int.MAX_VALUE) {
    QRCodeDetail(label) {
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun QRCodeDetail(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.subtitle1.copy(fontSize = 12.sp, textAlign = TextAlign.End),
            modifier = Modifier
                .alignBy(FirstBaseline)
                .width(80.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .alignBy(FirstBaseline)
                .widthIn(min = 100.dp)
        ) {
            content()
        }
    }
}

@Composable
fun CopyShareEditButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
    maxWidth: Dp,
) {
    Row(modifier = Modifier.padding(horizontal = 4.dp)) {
        BorderButton(icon = R.drawable.ic_copy, onClick = onCopy)
        Spacer(modifier = Modifier.width(if (maxWidth <= 360.dp) 12.dp else 16.dp))
        BorderButton(icon = R.drawable.ic_share, onClick = onShare)
        if (onEdit != null) {
            Spacer(modifier = Modifier.width(if (maxWidth <= 360.dp) 12.dp else 16.dp))
            BorderButton(
                text = if (maxWidth <= 360.dp) null else stringResource(id = R.string.receive_lightning_edit_button),
                icon = R.drawable.ic_edit,
                onClick = onEdit
            )
        }
    }
}
