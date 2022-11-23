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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.utils.Converter.proportionalFeeAsPercentage
import fr.acinq.phoenix.android.utils.Converter.proportionalFeeAsPercentageString
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.data.WalletContext
import kotlinx.coroutines.launch
import java.text.DecimalFormat

private enum class IntroViewSteps {
    WELCOME, CHANNELS_INFO, SELF_CUSTODY
}

@Composable
fun IntroView(
    onBackClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(IntroViewSteps.WELCOME) }
    val walletParams = business.appConfigurationManager.chainContext.collectAsState()

    when (step) {
        IntroViewSteps.WELCOME -> WelcomeView(
            onBackClick = { Unit },
            onNextClick = { step = IntroViewSteps.CHANNELS_INFO }
        )
        IntroViewSteps.CHANNELS_INFO -> ChannelsInfoView(
            onBackClick = { step = IntroViewSteps.WELCOME },
            onNextClick = { step = IntroViewSteps.SELF_CUSTODY },
            walletParams.value,
        )
        IntroViewSteps.SELF_CUSTODY -> SelfCustodyView(
            onBackClick = { step = IntroViewSteps.CHANNELS_INFO },
            onNextClick = {
                scope.launch {
                    InternalData.saveShowIntro(context, false)
                    onFinishClick()
                }
            }
        )
    }
}

@Composable
private fun WelcomeView(
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    BackHandler() {
        onBackClick()
    }
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp, horizontal = 32.dp)
            .widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(id = R.string.intro_welcome_title),
            style = MaterialTheme.typography.h1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.intro_welcome_sub1), textAlign = TextAlign.Center)
        Spacer(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 16.dp)
        )
        FilledButton(text = stringResource(id = R.string.intro_welcome_next_button), onClick = onNextClick, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChannelsInfoView(
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    walletContext: WalletContext.V0.ChainContext?,
) {
    BackHandler() {
        onBackClick()
    }
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp, horizontal = 32.dp)
            .widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(id = R.string.intro_channels_title),
            style = MaterialTheme.typography.h3,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        val (feePercent, minFeeAmount) = walletContext?.let {
            DecimalFormat("0.####").format(it.payToOpen.v1.feePercent * 100) to it.payToOpen.v1.minFeeSat.sat
        } ?: ("???" to "???")
        Text(text = stringResource(id = R.string.intro_channels_sub1, feePercent, minFeeAmount), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.intro_channels_sub2), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))

        Spacer(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 16.dp)
        )
        FilledButton(text = stringResource(id = R.string.intro_channels_next_button), onClick = onNextClick, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SelfCustodyView(
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    BackHandler() {
        onBackClick()
    }
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp, horizontal = 32.dp)
            .widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(text = stringResource(id = R.string.intro_selfcustody_title), style = MaterialTheme.typography.h3, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = stringResource(id = R.string.intro_selfcustody_sub1), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.intro_selfcustody_sub2), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 16.dp)
        )
        FilledButton(text = stringResource(id = R.string.intro_selfcustody_next_button), icon = R.drawable.ic_zap, onClick = onNextClick, modifier = Modifier.fillMaxWidth())
    }
}
