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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import fr.acinq.phoenix.android.R


@Composable
fun SplashLayout(
    header: @Composable () -> Unit,
    topContent: @Composable ColumnScope.() -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit,
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val (decorCurveConstraint, decorFillerConstraint, contentConstraint) = createRefs()

        // curve
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .constrainAs(decorCurveConstraint) {
                    top.linkTo(parent.top, margin = 180.dp)
                },
            painter = painterResource(id = R.drawable.payment_splash_curve),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(MaterialTheme.colors.surface)
        )

        // filler background with surface color
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .constrainAs(decorFillerConstraint) {
                    top.linkTo(decorCurveConstraint.bottom)
                    bottom.linkTo(parent.bottom)
                    height = Dimension.fillToConstraints
                }
        ) { }

        // content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(contentConstraint) {
                    top.linkTo(parent.top)
                }
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            header()
            Column(
                modifier = Modifier
                    .heightIn(min = 260.dp)
                    .padding(bottom = 30.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                topContent()
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                bottomContent()
            }
        }
    }
}

@Composable
fun SplashLabelRow(
    label: String,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.typography.subtitle1.color,
    helpMessage: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Row(modifier = Modifier
            .weight(1f)
            .alignByBaseline(),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (helpMessage != null) {
                IconPopup(modifier = Modifier.offset(y = (-3).dp), popupMessage = helpMessage, spaceLeft = 0.dp, spaceRight = 4.dp)
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.subtitle1.copy(fontSize = 12.sp, textAlign = TextAlign.End),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Image(
                    painter = painterResource(id = icon),
                    colorFilter = ColorFilter.tint(iconTint),
                    contentDescription = null,
                    modifier = Modifier
                        .size(ButtonDefaults.IconSize)
                        .offset(y = (-2).dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1.6f)
                .alignByBaseline(),
        ) {
            content()
        }
    }
}