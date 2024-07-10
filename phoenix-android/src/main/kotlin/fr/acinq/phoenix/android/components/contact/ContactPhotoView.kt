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
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.mutedBgColor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ContactPhotoView(
    photoUri: String?,
    name: String?,
    onChange: ((String?) -> Unit)?,
    imageSize: Dp = 96.dp,
    borderSize: Dp? = null
) {
    val context = LocalContext.current

    var isProcessing by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember(photoUri) { mutableStateOf(photoUri) }
    val bitmap: ImageBitmap? = remember(fileName) {
        fileName?.let { ContactPhotoHelper.getPhotoForFile(context, it) }
    }

    var cameraAccessDenied by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = {
            val cacheUri = tempPhotoUri
            if (it && cacheUri != null) {
                val newFileName = ContactPhotoHelper.createPermaContactPicture(context, cacheUri)
                fileName = newFileName
                if (fileName != null) {
                    onChange?.invoke(fileName)
                }
            }
            isProcessing = false
        }
    )

    Surface(
        shape = CircleShape,
        border = borderSize?.let { BorderStroke(width = it, color = MaterialTheme.colors.surface) },
        elevation = borderSize?.let { 1.dp } ?: 0.dp,
        modifier = if (onChange != null) {
            Modifier.clickable(
                role = Role.Button,
                onClick = {
                    Toast.makeText(context, "\uD83D\uDEA7 Coming soon!", Toast.LENGTH_SHORT).show()
                    return@clickable
                    if (isProcessing) return@clickable
                    if (cameraPermissionState.status.isGranted) {
                        isProcessing = true
                        if (tempPhotoUri == null) {
                            ContactPhotoHelper.createTempContactPictureUri(context)?.let {
                                tempPhotoUri = it
                                cameraLauncher.launch(tempPhotoUri)
                            }
                        } else {
                            cameraLauncher.launch(tempPhotoUri)
                        }
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
                },
                enabled = !isProcessing
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

object ContactPhotoHelper {

    val log = LoggerFactory.getLogger(this::class.java)

    fun createTempContactPictureUri(
        context: Context,
    ): Uri? {
        val cacheContactsDir = File(context.cacheDir, "contacts")
        if (!cacheContactsDir.exists()) cacheContactsDir.mkdir()
        return try {
            val tempFile = File.createTempFile("contact_", ".png", cacheContactsDir)
            tempFile.deleteOnExit()
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", tempFile)
        } catch (e: Exception) {
            log.error("failed to write temporary file for contact: {}", e.localizedMessage)
            null
        }
    }

    fun createPermaContactPicture(
        context: Context,
        tempFileUri: Uri,
    ): String? {
        val contactsDir = File(context.filesDir, "contacts")
        if (!contactsDir.exists()) contactsDir.mkdir()

        return try {
            val bitmap = context.contentResolver.openInputStream(tempFileUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            val scale = 480f / Math.max(bitmap.width, bitmap.height)
            val matrix = Matrix().apply { postScale(scale, scale) }
            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)

            val photoFile = File(contactsDir, "contact_${currentTimestampMillis()}.jpg")
            FileOutputStream(photoFile).use { scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            photoFile.name
        } catch (e: Exception) {
            log.error("failed to write contact photo to disk: {}", e.localizedMessage)
            null
        }
    }

    fun getPhotoForFile(
        context: Context,
        fileName: String
    ): ImageBitmap? {
        val contactsDir = File(context.filesDir, "contacts")
        if (!contactsDir.exists() || !contactsDir.canRead()) return null

        return try {
            val photoFile = File(contactsDir, fileName)
            val content = photoFile.readBytes()
            BitmapFactory.decodeByteArray(content, 0, content.size).asImageBitmap()
        } catch (e: Exception) {
            log.info("could not read contact photo=$fileName: ", e.localizedMessage)
            null
        }
    }
}
