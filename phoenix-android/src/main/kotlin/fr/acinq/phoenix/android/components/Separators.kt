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

package fr.acinq.phoenix.android.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.utils.borderColor


@Composable
fun HSeparator(
    modifier: Modifier = Modifier,
    color: Color = borderColor,
    width: Dp? = null,
) {
    Box(
        (width?.run { modifier.width(width) } ?: modifier.fillMaxWidth())
            .height(1.dp)
            .background(color = color)
    )
}

@Composable
fun VSeparator(
    padding: PaddingValues = PaddingValues(0.dp),
    color: Color = borderColor,
    height: Dp? = null
) {
    Box(
        (height?.run { Modifier.height(height) } ?: Modifier.fillMaxHeight())
            .width(1.dp)
            .padding(padding)
            .background(color = color)
    )
}

@Composable
fun PrimarySeparator(
    modifier: Modifier = Modifier, width: Dp = 60.dp, height: Dp = 8.dp, color: Color = MaterialTheme.colors.primary
) {
    Surface(
        shape = CircleShape,
        color = color,
        modifier = modifier
            .width(width)
            .height(height)
    ) { }
}

fun Modifier.enableOrFade(enabled: Boolean): Modifier = this.then(Modifier.alpha(if (enabled) 1f else 0.65f))
