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

package fr.acinq.phoenix.android.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Button

@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        Text(text = message)
    }
}

@Composable
fun ConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            Button(text = stringResource(id = R.string.btn_cancel), onClick = onDismiss, shape = RoundedCornerShape(16.dp))
            Button(text = stringResource(id = R.string.btn_confirm), onClick = onConfirm, shape = RoundedCornerShape(16.dp))
        }
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = if (title == null) 24.dp else 0.dp)) {
            content()
        }
    }
}