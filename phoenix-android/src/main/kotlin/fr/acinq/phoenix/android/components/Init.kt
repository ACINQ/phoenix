/*
 * Copyright 2022 ACINQ SAS
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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.utils.mutedTextColor

@Composable
fun InitScreen(isScrollable: Boolean = true, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .then(if (isScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
    ) {
        content()
    }
}

@Composable
fun InitHeader(
    title: String? = null,
    subtitle: String? = null,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp),
    ) {
        BackButton(onClick = onBackClick)
        Column(
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            title?.run { Text(text = this) }
            subtitle?.run {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = this, style = TextStyle(color = mutedTextColor(), fontSize = 14.sp))
            }
        }
    }
}