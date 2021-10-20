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

package fr.acinq.phoenix.android.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.IconWithText
import fr.acinq.phoenix.android.databinding.ScanViewBinding
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.readClipboard


@ExperimentalMaterialApi
@Composable
fun ReadDataView() {
    requireWalletPresent(inScreen = Screen.ReadData) {
        val context = LocalContext.current.applicationContext
        val nc = navController
        val log = logger()
        fun handleInput(input: String) {
            // check input is valid - hold it in a viewmodel, maybe using the controller's
            log.info { "read data=$input" }
            nc.navigate(Screen.Send, input)
        }
        Box(modifier = Modifier) {
            AndroidViewBinding(ScanViewBinding::inflate) {
                scanView.initializeFromIntent(Intent().apply {
                    putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
                    putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.name)
                })
                scanView.decodeContinuous(object : BarcodeCallback {
                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.let {
                            scanView.pause()
                            handleInput(it)
                        }
                    }
                })
                scanView.resume()
            }
            Box(
                Modifier
                    .width(dimensionResource(id = R.dimen.scanner_size))
                    .height(dimensionResource(id = R.dimen.scanner_size))
                    .clip(RoundedCornerShape(24.dp))
                    .background(whiteLowOp())
                    .align(Alignment.Center)
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                FlatButton({ readClipboard(context)?.let { handleInput(it) } }, Modifier.fillMaxWidth()) {
                    IconWithText(icon = R.drawable.ic_clipboard, text = stringResource(id = R.string.send_init_paste))
                }
                FlatButton({ nc.popBackStack() }, Modifier.fillMaxWidth()) {
                    IconWithText(icon = R.drawable.ic_arrow_back, text = stringResource(id = R.string.btn_cancel))
                }
            }
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun FlatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        elevation = null,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = colors,
        modifier = modifier
    ) {
        content()
    }
}