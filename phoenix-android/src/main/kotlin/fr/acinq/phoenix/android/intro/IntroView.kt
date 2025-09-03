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

package fr.acinq.phoenix.android.intro

import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.utils.extensions.findActivity
import kotlinx.coroutines.*


@Composable
fun IntroView(
    onFinishClick: () -> Unit
) {
    val context = LocalContext.current
    val globalPrefs = application.globalPrefs
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    HorizontalPager(state = pagerState) { index ->
        BackHandler { context.findActivity().moveTaskToBack(false) }
        when (index) {
            0 -> WelcomeView(
                onNextClick = { scope.launch { pagerState.animateScrollToPage(1) } }
            )
            1 -> ChannelsInfoView(
                onNextClick = { scope.launch { pagerState.animateScrollToPage(2) } },
            )
            2 -> SelfCustodyView(
                onNextClick = {
                    scope.launch {
                        globalPrefs.saveShowIntro(false)
                        onFinishClick()
                    }
                }
            )
        }
    }
}

@Composable
private fun WelcomeView(
    onNextClick: () -> Unit,
) {
    IntroLayout(
        topContent = {
            Image(painter = painterResource(id = R.drawable.intro_btc), contentDescription = "bitcoin logo")
        },
        bottomContent = {
            Text(
                text = stringResource(id = R.string.intro_welcome_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.h2,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.intro_welcome_sub1),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        navContent = {
            FilledButton(text = stringResource(id = R.string.intro_welcome_next_button), onClick = onNextClick, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
private fun ChannelsInfoView(
    onNextClick: () -> Unit,
) {
    IntroLayout(
        topContent = {
            Image(painter = painterResource(id = R.drawable.intro_ln), contentDescription = "lightning channels")
        },
        bottomContent = {
            Text(
                text = stringResource(id = R.string.intro_channels_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.h3,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.intro_channels_sub1), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
       },
        navContent = {
            FilledButton(text = stringResource(id = R.string.intro_channels_next_button), onClick = onNextClick, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
private fun SelfCustodyView(
    onNextClick: () -> Unit,
) {
    IntroLayout(
        topContent = {
            Image(painter = painterResource(id = R.drawable.intro_cust), contentDescription = "your key, your bitcoins")
        },
        bottomContent = {
            Text(
                text = stringResource(id = R.string.intro_selfcustody_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.h3,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.intro_selfcustody_sub1), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.intro_selfcustody_sub2), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        navContent = {
            FilledButton(text = stringResource(id = R.string.intro_selfcustody_next_button), icon = R.drawable.ic_zap, onClick = onNextClick, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
private fun IntroLayout(
    topContent: @Composable ColumnScope.() -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit,
    navContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 16.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f, fill = true),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            topContent()
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f)
                .widthIn(max = 400.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            bottomContent()
            Spacer(modifier = Modifier.height(32.dp))
            navContent()
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}
