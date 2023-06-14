/*
 * Copyright 2021 ACINQ SAS
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.mutedTextColor


/** Button for navigation purpose, with the back arrow. */
@Composable
fun BackButton(onClick: () -> Unit) {
    BackHandler(onBack = onClick)
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 50.dp, bottomEnd = 50.dp, bottomStart = 0.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Unspecified,
            disabledBackgroundColor = Color.Unspecified,
            contentColor = MaterialTheme.colors.onSurface,
            disabledContentColor = mutedTextColor,
        ),
        elevation = null,
        modifier = Modifier.size(width = 58.dp, height = 52.dp)
    ) {
        PhoenixIcon(resourceId = R.drawable.ic_arrow_back, Modifier.width(24.dp))
    }
}

@Composable
fun BackButtonWithBalance(
    onBackClick: () -> Unit,
    balance: MilliSatoshi?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BackButton(onClick = onBackClick)
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(id = R.string.send_balance_prefix).uppercase(),
                style = MaterialTheme.typography.body1.copy(color = mutedTextColor, fontSize = 12.sp),
                modifier = Modifier.alignBy(FirstBaseline)
            )
            Spacer(modifier = Modifier.width(4.dp))
            balance?.let {
                AmountView(amount = it, modifier = Modifier.alignBy(FirstBaseline))
            }
        }
    }
}

/** The default screen is a full-height, full-width column with the material theme's background color. It is scrollable by default. */
@Composable
fun DefaultScreenLayout(
    isScrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    backgroundColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize() // cancelled if scrollable!
            .then(if (isScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .then(if (backgroundColor != null) Modifier.background(backgroundColor) else Modifier),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        content()
    }
}

/** The default header of a screen contains a back button and an optional title. */
@Composable
fun DefaultScreenHeader(
    title: String? = null,
    onBackClick: () -> Unit,
    backgroundColor: Color = Color.Transparent,
) {
    DefaultScreenHeader(
        content = {
            title?.let { Text(text = it) }
        },
        onBackClick = onBackClick,
        backgroundColor = backgroundColor
    )
}

/** A header with a back button, a title, and a help button. */
@Composable
fun DefaultScreenHeader(
    title: String,
    helpMessage: String,
    helpMessageLink: Pair<String, String>? = null,
    onBackClick: () -> Unit,
    backgroundColor: Color = Color.Transparent,
) {
    DefaultScreenHeader(
        content = {
            Row {
                Text(text = title)
                Spacer(modifier = Modifier.width(4.dp))
                IconPopup(popupMessage = helpMessage, popupLink = helpMessageLink)
            }
        },
        onBackClick = onBackClick,
        backgroundColor = backgroundColor
    )
}

/** The default header of a screen contains a back button and some content. */
@Composable
fun DefaultScreenHeader(
    onBackClick: () -> Unit,
    backgroundColor: Color = Color.Transparent,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = 0.dp, top = 2.dp, bottom = 2.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBackClick)
        content()
    }
}

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
    padding: PaddingValues = PaddingValues(0.dp)
) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .padding(padding)
            .background(color = borderColor)
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
