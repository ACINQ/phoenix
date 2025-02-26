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


package fr.acinq.phoenix.android.components.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.utils.images.ZxingQrCodeAnalyzer
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds


@SuppressLint("ClickableViewAccessibility")
@Composable
fun BoxScope.ScannerView(
    onScannedText: (String) -> Unit,
    onDismiss: () -> Unit,
    isPaused: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyserExecutor = remember { Executors.newSingleThreadExecutor() }

    val cameraController = remember { LifecycleCameraController(context) }
    var scannedText by remember { mutableStateOf<String?>(null) }

    // let us execute the callback for an already scanned text with a throttle
    var scanResetTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(2.seconds)
            scanResetTick++
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val view = PreviewView(context)
            view.post {
                cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                cameraController.imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(ZxingQrCodeAnalyzer.DEFAULT_RESOLUTION, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    .build()
                cameraController.setImageAnalysisAnalyzer(analyserExecutor, ZxingQrCodeAnalyzer { result ->
                    scannedText = result.text.trim().takeIf { it.isNotBlank() }
                })
                cameraController.bindToLifecycle(lifecycleOwner)
                view.controller = cameraController
            }
            view
        }
    )

    Column(modifier = Modifier
        .align(Alignment.BottomCenter)
        .background(Brush.verticalGradient(colorStops = arrayOf(0.1f to Color.Transparent, 1f to Color(0x44000000))))
        .padding(24.dp)
        .systemGestureExclusion()
    ) {
        Spacer(Modifier.height(36.dp))
        TextWithIcon(
            text = "Zoom or tap the QR to focus",
            icon = R.drawable.ic_tap,
            textStyle = MaterialTheme.typography.body1.copy(color = Color.White),
            iconTint = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(24.dp))
        FilledButton(
            text = stringResource(id = R.string.btn_cancel),
            icon = R.drawable.ic_arrow_back,
            iconTint = MaterialTheme.colors.onSurface,
            backgroundColor = MaterialTheme.colors.surface,
            textStyle = MaterialTheme.typography.button,
            padding = PaddingValues(16.dp),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }

    CameraPermissionsView()

    LaunchedEffect(scannedText, scanResetTick) {
        scannedText?.let {
            if (!isPaused) onScannedText(it)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun BoxScope.CameraPermissionsView() {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (!cameraPermissionState.status.isGranted) {
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
