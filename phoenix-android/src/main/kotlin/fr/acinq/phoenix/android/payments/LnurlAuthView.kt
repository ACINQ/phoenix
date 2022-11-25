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

package fr.acinq.phoenix.android.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.lnurl.LnurlAuth

@Composable
fun LnurlAuthView(
    model: Scan.Model.LnurlAuthFlow,
    onBackClick: () -> Unit,
    onLoginClick: (Scan.Intent.LnurlAuthFlow) -> Unit,
    onAuthSchemeInfoClick: () -> Unit
) {
    val log = logger("LnurlAuthView")

    val context = LocalContext.current
    var showHowItWorks by remember { mutableStateOf(false) }
    val prefAuthScheme by UserPrefs.getLnurlAuthScheme(context).collectAsState(initial = null)
    val isLegacyDomain = remember(model) { LnurlAuth.LegacyDomain.isEligible(model.auth.initialUrl) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 50.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DefaultScreenHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(16.dp))
        when (model) {
            is Scan.Model.LnurlAuthFlow.LoginRequest -> {
                val isLegacySchemeUsed = prefAuthScheme == LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME && isLegacyDomain
                if (showHowItWorks) {
                    HowItWorksDialog(
                        domain = LnurlAuth.LegacyDomain.filterDomain(model.auth.initialUrl),
                        isLegacySchemeUsed = isLegacySchemeUsed,
                        onAuthSchemeInfoClick = onAuthSchemeInfoClick,
                        onDismiss = { showHowItWorks = false }
                    )
                }
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(top = 32.dp, start= 32.dp, end = 32.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.lnurl_auth_instructions), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = model.auth.initialUrl.host, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
                    if (isLegacySchemeUsed) {
                        Text(
                            text = stringResource(R.string.lnurl_auth_legacy_notice),
                            style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center, fontSize = 12.sp),
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    HSeparator(width = 60.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        text = stringResource(R.string.lnurl_auth_help),
                        icon = R.drawable.ic_help_circle,
                        padding = PaddingValues(8.dp),
                        space = 8.dp,
                        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.primary, textAlign = TextAlign.Center, fontSize = 14.sp),
                        onClick = { showHowItWorks = true }
                    )
                }
                Spacer(Modifier.height(32.dp))
                FilledButton(
                    text = R.string.lnurl_auth_button,
                    icon = R.drawable.ic_key,
                    onClick = {
                        if (prefAuthScheme != null) {
                            onLoginClick(Scan.Intent.LnurlAuthFlow.Login(model.auth, minSuccessDelaySeconds = 1.0, scheme = prefAuthScheme ?: LnurlAuth.Scheme.DEFAULT_SCHEME))
                        }
                    },
                    enabled = prefAuthScheme != null
                )
            }
            is Scan.Model.LnurlAuthFlow.LoggingIn -> {
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.lnurl_auth_in_progress, model.auth.initialUrl.host), textAlign = TextAlign.Center)
                }
            }
            is Scan.Model.LnurlAuthFlow.LoginResult -> {
                val error = model.error
                if (error != null) {
                    Card(
                        externalPadding = PaddingValues(horizontal = 16.dp),
                        internalPadding = PaddingValues(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ErrorResponseView(error)
                        BorderButton(text = R.string.lnurl_auth_try_again_button, icon = R.drawable.ic_arrow_back, onClick = onBackClick)
                    }
                } else {
                    Card(
                        externalPadding = PaddingValues(horizontal = 16.dp),
                        internalPadding = PaddingValues(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = stringResource(id = R.string.lnurl_auth_success, model.auth.initialUrl.host), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(32.dp))
                    FilledButton(text = R.string.btn_ok, icon = R.drawable.ic_check, onClick = onBackClick)
                }
            }
        }
    }
}

@Composable
private fun ErrorResponseView(
    error: Scan.LoginError
) {
    Text(text = stringResource(R.string.lnurl_auth_failure), style = MaterialTheme.typography.body2)
    Spacer(Modifier.height(8.dp))
    Text(
        text = when (error) {
            is Scan.LoginError.ServerError -> error.details.details
            is Scan.LoginError.NetworkError -> stringResource(R.string.lnurl_auth_error_network)
            is Scan.LoginError.OtherError -> stringResource(R.string.lnurl_auth_error_other)
        },
        style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun HowItWorksDialog(
    domain: String,
    isLegacySchemeUsed: Boolean,
    onAuthSchemeInfoClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(title = stringResource(id = R.string.lnurl_auth_help_dialog_title), onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(id = R.string.lnurl_auth_help_dialog_general_details, domain))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.lnurl_auth_help_dialog_privacy_title), style = MaterialTheme.typography.body2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = stringResource(id = R.string.lnurl_auth_help_dialog_privacy_details))
        }
        if (isLegacySchemeUsed) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(id = R.string.lnurl_auth_help_dialog_compat_title), style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(id = R.string.lnurl_auth_help_dialog_compat_details))
                Spacer(modifier = Modifier.height(4.dp))
                InlineButton(
                    text = stringResource(id = R.string.lnurl_auth_help_dialog_compat_button),
                    icon = R.drawable.ic_settings,
                    space = 8.dp,
                    onClick = onAuthSchemeInfoClick
                )
            }
        }
    }
}
