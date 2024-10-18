/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send.lnurl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.managers.SendManager

@Composable
fun LnurlAuthView(
    auth: LnurlAuth,
    onBackClick: () -> Unit,
    onChangeAuthSchemeSettingClick: () -> Unit,
    onAuthDone: () -> Unit,
) {
    var showHowItWorks by remember { mutableStateOf(false) }
    val prefAuthScheme by userPrefs.getLnurlAuthScheme.collectAsState(initial = null)
    val isLegacyDomain = remember(auth) { LnurlAuth.LegacyDomain.isEligible(auth.initialUrl) }
    val vm = viewModel<LnurlAuthViewModel>(factory = LnurlAuthViewModel.Factory(business.sendManager))

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 50.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DefaultScreenHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(16.dp))
        when (val state = vm.state.value) {
            is LnurlAuthViewState.Init -> {
                val isLegacySchemeUsed = prefAuthScheme == LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME && isLegacyDomain
                if (showHowItWorks) {
                    HowItWorksDialog(
                        domain = LnurlAuth.LegacyDomain.filterDomain(auth.initialUrl),
                        isLegacySchemeUsed = isLegacySchemeUsed,
                        onChangeAuthSchemeSettingClick = onChangeAuthSchemeSettingClick,
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
                    Text(text = auth.initialUrl.host, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
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
                    text = stringResource(id = R.string.lnurl_auth_button),
                    icon = R.drawable.ic_key,
                    onClick = { vm.authenticateToDomain(auth, prefAuthScheme ?: LnurlAuth.Scheme.DEFAULT_SCHEME) },
                    enabled = prefAuthScheme != null
                )
            }
            is LnurlAuthViewState.LoggingIn -> {
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.lnurl_auth_in_progress, auth.initialUrl.host), textAlign = TextAlign.Center)
                }
            }
            is LnurlAuthViewState.AuthFailure -> {
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ErrorResponseView(state.error)
                    BorderButton(text = stringResource(R.string.lnurl_auth_try_again_button), icon = R.drawable.ic_arrow_back, onClick = onBackClick)
                }
            }
            is LnurlAuthViewState.Error -> {
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ErrorMessage(header = stringResource(R.string.lnurl_auth_failure), details = state.cause.message)
                    Spacer(Modifier.height(24.dp))
                    BorderButton(text = stringResource(R.string.lnurl_auth_try_again_button), icon = R.drawable.ic_arrow_back, onClick = onBackClick)
                }
            }
            is LnurlAuthViewState.AuthSuccess -> {
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(id = R.string.lnurl_auth_success, auth.initialUrl.host), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(32.dp))
                FilledButton(text = stringResource(id = R.string.btn_ok), icon = R.drawable.ic_check, onClick = onAuthDone)
            }
        }
    }
}

@Composable
private fun ErrorResponseView(
    error: SendManager.LnurlAuthError
) {
    Text(text = stringResource(R.string.lnurl_auth_failure), style = MaterialTheme.typography.body2)
    Spacer(Modifier.height(8.dp))
    Text(
        text = when (error) {
            is SendManager.LnurlAuthError.ServerError -> error.details.details
            is SendManager.LnurlAuthError.NetworkError -> stringResource(R.string.lnurl_auth_error_network)
            is SendManager.LnurlAuthError.OtherError -> stringResource(R.string.lnurl_auth_error_other)
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
    onChangeAuthSchemeSettingClick: () -> Unit,
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
                    onClick = onChangeAuthSchemeSettingClick
                )
            }
        }
    }
}
