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

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.dialogs.FullScreenDialog
import fr.acinq.phoenix.android.components.dialogs.IconTextPopup
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.images.QRCodeHelper
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.extensions.safeLet
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.android.utils.updateScreenBrightnesss
import kotlinx.coroutines.launch

@Composable
fun ReceiveView(
    onBackClick: () -> Unit,
    onFeeManagementClick: () -> Unit,
    onScanDataClick: () -> Unit,
) {
    val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory(business.chain, business.peerManager, business.nodeParamsManager, business.walletManager))

    DefaultScreenLayout(horizontalAlignment = Alignment.CenterHorizontally, isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            content = {
                Spacer(modifier = Modifier.weight(1f))
                TransparentFilledButton(
                    icon = R.drawable.ic_scan_qr,
                    iconTint = MaterialTheme.colors.primary,
                    onClick = onScanDataClick,
                    padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    space = 6.dp,
                )
                Spacer(modifier = Modifier.width(16.dp))
            },
        )
        ReceiveViewPages(vm, onFeeManagementClick)
    }
}

@Composable
private fun ReceiveViewPages(
    vm: ReceiveViewModel,
    onFeeManagementClick: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    BoxWithConstraints {
        HorizontalPager(
            modifier = Modifier.fillMaxHeight(),
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 44.dp),
        ) { index ->
            val columnWidth = remember(maxWidth) {
                when {
                    maxWidth <= 240.dp -> 160.dp
                    maxWidth <= 320.dp -> 240.dp
                    maxWidth <= 480.dp -> 270.dp
                    else -> 320.dp
                }
            }
            val topPadding = remember(maxHeight) {
                when {
                    maxHeight > 800.dp -> 80.dp
                    maxHeight > 600.dp -> 40.dp
                    maxHeight > 400.dp -> 20.dp
                    else -> 0.dp
                }
            }
            CompositionLocalProvider(
                LocalOverscrollFactory provides null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(topPadding))
                    when (index) {
                        0 -> {
                            val defaultInvoiceExpiry by userPrefs.getInvoiceDefaultExpiry.collectAsState(null)
                            val defaultInvoiceDesc by userPrefs.getInvoiceDefaultDesc.collectAsState(null)
                            safeLet(defaultInvoiceDesc, defaultInvoiceExpiry) { desc, expiry ->
                                LightningInvoiceView(
                                    vm = vm, onFeeManagementClick = onFeeManagementClick,
                                    defaultDescription = desc, defaultExpiry = expiry, columnWidth = columnWidth, isPageActive = pagerState.currentPage == 0
                                )
                            } ?: ProgressView(text = stringResource(id = R.string.utils_loading_prefs))
                        }
                        1 -> BitcoinAddressView(vm = vm, columnWidth = columnWidth)
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceHeader(
    text: String,
    icon: Int,
    helpMessage: String,
) {
    IconTextPopup(
        text = text,
        icon = icon,
        textStyle = MaterialTheme.typography.body1.copy(fontSize = 16.sp),
        popupMessage = helpMessage,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun QRCodeView(
    data: String?,
    bitmap: ImageBitmap?,
    width: Dp = Dp.Unspecified,
    loadingLabel: String,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = RoundedCornerShape(16.dp)
            )
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        QRCodeImage(bitmap = bitmap, onLongClick = { data?.let { copyToClipboard(context, it) } })
        if (bitmap == null) {
            Card(shape = RoundedCornerShape(16.dp)) { ProgressView(text = loadingLabel) }
        }
    }
}

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
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            DisposableEffect(key1 = Unit) {
                updateScreenBrightnesss(context, toMax = true)
                onDispose {
                    updateScreenBrightnesss(context, toMax = false)
                }
            }
            var isSavingToDisk by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp)
                ) { image() }
                if (bitmap != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        text = stringResource(id = R.string.btn_save),
                        icon = R.drawable.ic_image,
                        onClick = {
                            scope.launch {
                                isSavingToDisk = true
                                QRCodeHelper.saveQRToGallery(context, bitmap.asAndroidBitmap())
                                isSavingToDisk = false
                                Toast.makeText(context, "Image saved!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        padding = PaddingValues(12.dp),
                        enabled = !isSavingToDisk,
                        backgroundColor = MaterialTheme.colors.surface,
                        maxLines = 1,
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
fun QRCodeLabel(label: String, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.subtitle2, modifier = Modifier.alignByBaseline())
        Column(modifier = Modifier.alignByBaseline()) { content() }
    }
}

@Composable
fun CopyShareButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)) {
        BorderButton(icon = R.drawable.ic_copy, onClick = onCopy)
        Spacer(modifier = Modifier.width(16.dp))
        BorderButton(icon = R.drawable.ic_share, onClick = onShare)
    }
}

@Composable
fun CopyButtonDialog(label: String, value: String, icon: Int) {
    val context = LocalContext.current
    Clickable(onClick = { copyToClipboard(context, data = value) }, modifier = Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TextWithIcon(text = label, textStyle = MaterialTheme.typography.body2, icon = icon)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = value, style = MaterialTheme.typography.caption.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

@Composable
fun ShareButtonDialog(label: String, value: String, icon: Int) {
    val context = LocalContext.current
    Clickable(
        onClick = { share(context, value, context.getString(R.string.receive_lightning_share_subject), context.getString(R.string.receive_lightning_share_title)) },
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TextWithIcon(text = label, textStyle = MaterialTheme.typography.body2, icon = icon)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = value, style = MaterialTheme.typography.caption.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}
