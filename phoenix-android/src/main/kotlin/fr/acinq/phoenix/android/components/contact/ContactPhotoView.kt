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

package fr.acinq.phoenix.android.components.contact

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.borderColor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ContactPhotoView(
    photoUri: String?,
    name: String?,
    onChange: ((String?) -> Unit)?,
    imageSize: Dp = 96.dp,
) {
    val context = LocalContext.current

    val tempPhotoUri by remember { mutableStateOf(photoUri?.let { Uri.parse(it) }) }
    var realUri by remember { mutableStateOf<Uri?>(null) }
    val bitmap: ImageBitmap? = remember(tempPhotoUri) {
        realUri?.let { uri ->
            // TODO: load image from URI
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }
    }

    var cameraAccessDenied by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { realUri = tempPhotoUri }
    )
    Surface(
        shape = CircleShape,
        border = bitmap?.let { BorderStroke(width = 1.dp, color = borderColor) },
        modifier = if (onChange != null) {
            Modifier.clickable(
                role = Role.Button,
                onClick = {
                    Toast.makeText(context, "\uD83D\uDEA7 Coming soon!", Toast.LENGTH_SHORT).show()
                    return@clickable
                    if (cameraPermissionState.status.isGranted) {
                        cameraLauncher.launch(tempPhotoUri)
                    } else {
                        cameraAccessDenied = cameraPermissionState.status.shouldShowRationale
                        if (cameraAccessDenied) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            })
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                }
            )
        } else Modifier
    ) {
        if (bitmap == null) {
            Image(
                painter = painterResource(id = R.drawable.ic_contact_placeholder),
                colorFilter = ColorFilter.tint(borderColor),
                contentDescription = name,
                modifier = Modifier.size(imageSize)
            )
            if (cameraAccessDenied) {
                Text(text = stringResource(id = R.string.scan_request_camera_access_denied), style = MaterialTheme.typography.subtitle2)
            }
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier.size(imageSize),
                contentScale = ContentScale.Crop,
            )
        }
    }
}