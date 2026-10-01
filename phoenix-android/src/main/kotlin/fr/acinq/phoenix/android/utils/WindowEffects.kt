/*
 * Copyright 2026 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import fr.acinq.phoenix.android.utils.extensions.findActivitySafe

/**
 * Marks the view as secure to prevent screen capture. This is used on specific screens only and the effect
 * is disposed upon leaving the view.
 */
@Composable
fun MarkWindowSecure() {
    val window = LocalContext.current.findActivitySafe()?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Marks the view as sensitive for accessibility services, and disables overlays and obscured touches.
 *
 * We use a side-effect as setting the flags is cheap. We don't need a on-dispose callback either.
 * The flags are already set at the main activity's level, but elements like dialogs create their
 * their own window and their own view.
 */
@Composable
fun MarkDialogSensitive() {
    val view = LocalView.current
    SideEffect {
        view.rootView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (view.parent as? DialogWindowProvider)?.window?.setHideOverlayWindows(true)
        }
    }
}
