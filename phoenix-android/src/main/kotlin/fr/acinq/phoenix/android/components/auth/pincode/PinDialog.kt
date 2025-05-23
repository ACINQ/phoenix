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

package fr.acinq.phoenix.android.components.auth.pincode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.auth.pincode.PinDialog.PIN_LENGTH
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor

@Composable
internal fun BasePinDialog(
    prompt: @Composable () -> Unit,
    stateLabel: (@Composable () -> Unit)?,
    onDismiss: () -> Unit,
    onPinSubmit: (String) -> Unit,
    enabled: Boolean,
    initialPin: String = "",
) {
    var pinValue by remember(initialPin) { mutableStateOf(initialPin) }

    ModalBottomSheet(
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        internalPadding = PaddingValues(horizontal = 12.dp),
        containerColor = MaterialTheme.colors.background,
    ) {
        prompt()
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.background(MaterialTheme.colors.surface, shape = RoundedCornerShape(24.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 32.dp)) {
                if (stateLabel == null) {
                    PinDisplay(cursorPosition = -1)
                    PinDisplay(cursorPosition = pinValue.length)
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colors.surface) {
                        stateLabel()
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            PinKeyboard(
                onPinPress = { digit ->
                    when (pinValue.length + 1) {
                        in 0..<PIN_LENGTH -> {
                            pinValue += digit
                        }

                        PIN_LENGTH -> {
                            pinValue += digit
                            onPinSubmit(pinValue)
                        }

                        else -> {
                            // ignore or error
                        }
                    }
                },
                onResetPress = { pinValue = "" },
                onDeleteLAst = { pinValue = pinValue.dropLast(1) },
                isEnabled = enabled && pinValue.length in 0..6
            )
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

@Composable
private fun PinDisplay(cursorPosition: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(PIN_LENGTH) { shapePosition ->
            Surface(
                shape = CircleShape,
                color = if (cursorPosition > shapePosition) MaterialTheme.colors.onSurface else mutedTextColor,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                    .alpha(if (cursorPosition > shapePosition) 1f else 0.2f)
            ) {}
        }
    }
}

@Composable
fun PinDialogTitle(text: String, icon: Int? = null, tint: Color = MaterialTheme.colors.onSurface) {
    if (icon == null) {
        Text(text = text, style = MaterialTheme.typography.h4)
    } else {
        TextWithIcon(text = text, icon = icon, textStyle = MaterialTheme.typography.h4, iconTint = tint)
    }
}

@Composable
fun PinStateMessage(text: String, icon: Int? = null, tint: Color = MaterialTheme.colors.onSurface) {
    if (icon == null) {
        Text(text = text)
    } else {
        TextWithIcon(text = text, icon = icon, iconTint = tint)
    }
}

@Composable
fun PinStateError(text: String) {
    TextWithIcon(
        text = text,
        icon = R.drawable.ic_cross_circle,
        iconTint = negativeColor
    )
}

object PinDialog {
    const val PIN_LENGTH = 6
}