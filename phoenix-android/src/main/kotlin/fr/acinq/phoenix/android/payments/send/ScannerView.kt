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


package fr.acinq.phoenix.android.payments.send

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.databinding.ScanViewBinding

@Composable
fun ScannerView(
    onScanViewBinding: (DecoratedBarcodeView) -> Unit,
    onScannedText: (String) -> Unit
) {
    // scanner view using a legacy binding
    AndroidViewBinding(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red),
        factory = { inflater, viewGroup, attach ->
            val binding = ScanViewBinding.inflate(inflater, viewGroup, attach)
            binding.scanView.let { scanView ->
                scanView.initializeFromIntent(Intent().apply {
                    putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
                    putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.name)
                })
                scanView.decodeContinuous(object : BarcodeCallback {
                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.trim()?.takeIf { it.isNotBlank() }?.let {
                            scanView.pause()
                            onScannedText(it)
                        }
                    }
                })
                onScanViewBinding(scanView)
                scanView.resume()
            }
            binding
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BoxScope.CameraPermissionsView(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (cameraPermissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        Card(
            modifier = Modifier.align(Alignment.Center),
        ) {
            // if user has denied permission, open the system settings for Phoenix
            val isDenied = cameraPermissionState.status.shouldShowRationale
            Button(
                icon = R.drawable.ic_camera,
                text = stringResource(id = if (isDenied) R.string.scan_request_camera_access_denied else R.string.scan_request_camera_access),
                onClick = {
                    if (isDenied) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            )
        }
    }
}
