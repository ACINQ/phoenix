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

package fr.acinq.phoenix.android.settings


import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.annotatedStringResource


@Composable
fun AboutView() {
    val nc = navController
    val context = LocalContext.current

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.about_title))
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = annotatedStringResource(id = R.string.about_general_content))

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = annotatedStringResource(id = R.string.about_seed_title), style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = annotatedStringResource(id = R.string.about_seed_content))

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = annotatedStringResource(id = R.string.about_rates_title), style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = annotatedStringResource(id = R.string.about_rates_content))
        }
        Card {
            Text(
                text = annotatedStringResource(id = R.string.about_version, BuildConfig.VERSION_NAME),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
        Card {
            SettingButton(
                text = stringResource(id = R.string.about_faq_link),
                icon = R.drawable.ic_help_circle,
                onClick = { openLink(context, "https://phoenix.acinq.co/faq") }
            )
            SettingButton(
                text = stringResource(R.string.about_support_link),
                icon = R.drawable.ic_message_circle,
                onClick = { openLink(context, "https://phoenix.acinq.co/support") }
            )
        }
        Card {
            SettingButton(
                text = stringResource(R.string.about_privacy_link),
                icon = R.drawable.ic_shield,
                onClick = { openLink(context, "https://phoenix.acinq.co/privacy") }
            )
            SettingButton(
                text = stringResource(R.string.about_terms_link),
                icon = R.drawable.ic_text,
                onClick = { openLink(context, "https://phoenix.acinq.co/terms") }
            )
        }
    }
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    AboutView()
}