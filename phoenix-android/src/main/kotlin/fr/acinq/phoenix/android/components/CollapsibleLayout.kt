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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.*

@OptIn(ExperimentalMotionApi::class)
@Composable
fun ToolBarLazyExampleDsl() {
    val defaultHeight = 250.dp
    val collapsedHeight = 50.dp
    val scene = MotionScene {
        val title = createRefFor("title")
        val image = createRefFor("image")
        val icon = createRefFor("icon")

        val start1 = constraintSet {
            constrain(title) {
                bottom.linkTo(image.bottom)
                start.linkTo(image.start)
            }
            constrain(image) {
                width = Dimension.matchParent
                height = Dimension.value(defaultHeight)
                top.linkTo(parent.top)
                customColor("cover", Color(0x000000FF))
            }
            constrain(icon) {
                top.linkTo(image.top, 16.dp)
                start.linkTo(image.start, 16.dp)
                alpha = 0f
            }
        }

        val end1 = constraintSet {
            constrain(title) {
                bottom.linkTo(image.bottom)
                start.linkTo(icon.end)
                centerVerticallyTo(image)
                scaleX = 0.7f
                scaleY = 0.7f
            }
            constrain(image) {
                width = Dimension.matchParent
                height = Dimension.value(collapsedHeight)
                top.linkTo(parent.top)
                customColor("cover", Color(0xFF0000FF))
            }
            constrain(icon) {
                top.linkTo(image.top, 16.dp)
                start.linkTo(image.start, 16.dp)
            }
        }
        transition(start1, end1, "default") {}
    }

    val maxPx = with(LocalDensity.current) { defaultHeight.roundToPx().toFloat() }
    val minPx = with(LocalDensity.current) { collapsedHeight.roundToPx().toFloat() }
    val toolbarHeight = remember { mutableStateOf(maxPx) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val height = toolbarHeight.value

                if (height + available.y > maxPx) {
                    toolbarHeight.value = maxPx
                    return Offset(0f, maxPx - height)
                }

                if (height + available.y < minPx) {
                    toolbarHeight.value = minPx
                    return Offset(0f, minPx - height)
                }

                toolbarHeight.value += available.y
                return Offset(0f, available.y)
            }
        }
    }

    val progress = 1 - (toolbarHeight.value - minPx) / (maxPx - minPx)

    Column {
        MotionLayout(
            motionScene = scene,
            progress = progress
        ) {
            Image(
                modifier = Modifier
                    .layoutId("image")
                    .background(customColor("image", "cover")),
                painter = painterResource(fr.acinq.phoenix.android.R.drawable.intro_btc),
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
            Image(
                modifier = Modifier.layoutId("icon"),
                painter = painterResource(fr.acinq.phoenix.android.R.drawable.ic_phoenix),
                contentDescription = null
            )
            Text(
                modifier = Modifier.layoutId("title"),
                text = "San Francisco",
                fontSize = 30.sp,
                color = Color.White
            )
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
        ) {
            items(100) {
                Text(text = "item $it", modifier = Modifier.padding(4.dp))
            }
        }
    }
}