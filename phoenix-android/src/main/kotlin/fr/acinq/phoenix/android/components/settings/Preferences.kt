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

package fr.acinq.phoenix.android.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.Dialog


internal data class PreferenceItem<T>(val item: T, val title: String, val description: String? = null)

@Composable
internal fun <T> ListPreferenceButton(
    title: String,
    subtitle: @Composable ColumnScope.() -> Unit = {},
    enabled: Boolean,
    selectedItem: T,
    preferences: List<PreferenceItem<T>>,
    onPreferenceSubmit: (PreferenceItem<T>) -> Unit,
    dialogTitle: String? = null,
    dialogDescription: String? = null,
    initialShowDialog: Boolean = false,
) {
    var showPreferenceDialog by remember { mutableStateOf(initialShowDialog) }

    Setting(title = title, subtitle = subtitle, enabled = enabled, onClick = { showPreferenceDialog = true })

    if (showPreferenceDialog) {
        ListPreferenceDialog(
            title = dialogTitle ?: title,
            description = dialogDescription,
            initialPrefIndex = preferences.map { it.item }.indexOf(selectedItem).takeIf { it >= 0 },
            preferences = preferences,
            onSubmit = {
                showPreferenceDialog = false
                onPreferenceSubmit(it)
            },
            onCancel = { showPreferenceDialog = false }
        )
    }
}

@Composable
private fun <T> ListPreferenceDialog(
    title: String,
    description: String?,
    initialPrefIndex: Int?,
    preferences: List<PreferenceItem<T>>,
    onSubmit: (PreferenceItem<T>) -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        title = title,
        onDismiss = onCancel,
        isScrollable = false,
        buttons = null // we add buttons manually as they would otherwise be pushed below the column which is not good ux in this case
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (description != null) {
                    Text(text = description, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(preferences) { index, item ->
                        PreferenceDialogItem(
                            item = item,
                            selected = index == initialPrefIndex,
                            onClick = onSubmit
                        )
                    }
                }
            }
            Button(
                onClick = onCancel,
                text = stringResource(id = R.string.btn_cancel),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun <T> PreferenceDialogItem(
    item: PreferenceItem<T>,
    selected: Boolean,
    onClick: (PreferenceItem<T>) -> Unit,
) {
    Clickable(onClick = { onClick(item) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            RadioButton(selected = selected, onClick = { onClick(item) })
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(text = item.title)
                item.description?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = it, style = MaterialTheme.typography.subtitle2)
                }
            }
        }
    }
}
