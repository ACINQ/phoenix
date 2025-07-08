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

package fr.acinq.phoenix.android.utils.converters

import android.annotation.SuppressLint
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

object DateFormatter {

    /** Converts this millis timestamp into a relative string date. */
    @Composable
    fun Long.toRelativeDateString(): String {
        val now = System.currentTimeMillis()
        val delay: Long = this - now
        return if (abs(delay) < 60 * 1000L) { // less than 1 minute ago
            stringResource(id = R.string.utils_date_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(this, now, delay).toString()
        }
    }

    /** Converts this millis timestamp into a pretty, absolute string date time using the locale format. */
    fun Long.toAbsoluteDateTimeString(): String = DateFormat.getDateTimeInstance().format(Date(this))

    /** Converts this millis timestamp into a pretty, absolute string date using the locale format. */
    fun Long.toAbsoluteDateString(): String = DateFormat.getDateInstance().format(Date(this))

    /** Converts this millis timestamp into an year-month-day string. */
    @SuppressLint("SimpleDateFormat")
    fun Long.toBasicAbsoluteDateString(): String = SimpleDateFormat("yyyy-MM-dd").format(Date(this))

    /** Converts this millis timestamp into an year-month-day string. */
    @SuppressLint("SimpleDateFormat")
    fun Long.toBasicAbsoluteDateTimeString(): String = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(this))
}
