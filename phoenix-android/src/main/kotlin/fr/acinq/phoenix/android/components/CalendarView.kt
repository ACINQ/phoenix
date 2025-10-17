/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.components

import android.widget.CalendarView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toAbsoluteDateString
import fr.acinq.phoenix.android.utils.mutedBgColor
import kotlinx.datetime.*
import kotlin.time.ExperimentalTime

/**
 * Calendar component to pick a day. [onDateSelected] returns the timestamp in millis at the
 * **start** of day.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun CalendarView(
    label: String,
    buttonBackgroundColor: Color = mutedBgColor,
    initialTimestampMillis: Long = currentTimestampMillis(),
    dialogTitle: String? = null,
    onDateSelected: (Long) -> Unit,
    enabled: Boolean,
) {
    var showCalendar by remember { mutableStateOf(false) }
    var timestampMillis by remember { mutableStateOf(initialTimestampMillis) }

    Row {
        Text(
            text = label,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = stringResource(id = R.string.component_calendar_inclusive),
            style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
            modifier = Modifier.alignByBaseline()
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            icon = R.drawable.ic_calendar,
            text = timestampMillis.toAbsoluteDateString(),
            onClick = { showCalendar = true },
            backgroundColor = buttonBackgroundColor,
            padding = PaddingValues(8.dp),
            space = 8.dp,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.alignByBaseline()
        )
    }
    if (showCalendar) {
        Dialog(onDismiss = { showCalendar = false }, title = dialogTitle) {
            AndroidView(
                modifier = Modifier.wrapContentSize(),
                factory = {
                    CalendarView(it)
                },
                update = { view ->
                    view.date = timestampMillis
                    view.setOnDateChangeListener { _, year, month, dayOfMonth ->
                        timestampMillis = LocalDate(year, month + 1, dayOfMonth).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                        onDateSelected(timestampMillis)
                        showCalendar = false
                    }
                }
            )
        }
    }
}