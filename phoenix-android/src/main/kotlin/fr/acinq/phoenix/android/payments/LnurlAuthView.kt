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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.LNUrl

@Composable
fun LnurlAuthView(
    model: Scan.Model.LnurlAuthFlow,
    onBackClick: () -> Unit,
    onLoginClick: (Scan.Intent.LnurlAuthFlow) -> Unit
) {
    val log = logger("LnurlAuthView")
    log.info { "compose lnurl-auth view with model=${model}" }

    val context = LocalContext.current
    val prefAuthKeyTypeState = UserPrefs.getLnurlAuthKeyType(context).collectAsState(initial = null)
    val prefAuthKeyType = prefAuthKeyTypeState.value
    val selectedAuthKeyType = remember { mutableStateOf(prefAuthKeyTypeState.value) }

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
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.lnurl_auth_instructions), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = model.auth.url.host, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.lnurl_auth_instructions_2), style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center, fontSize = 12.sp))
                    Spacer(modifier = Modifier.height(32.dp))
                    Label(text = stringResource(id = R.string.lnurl_auth_keytype_label)) {
                        if (prefAuthKeyType == null) {
                            Text(text = stringResource(id = R.string.lnurl_auth_keytype_wait), modifier = Modifier.padding(16.dp))
                        } else {
                            LnurlAuthKeyPicker(
                                initialKeyType = prefAuthKeyType,
                                onAuthKeyTypeChange = {
                                    selectedAuthKeyType.value = it
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                FilledButton(
                    text = R.string.lnurl_auth_button,
                    icon = R.drawable.ic_key,
                    onClick = {
                        val keyType = selectedAuthKeyType.value
                        if (keyType != null) {
                            onLoginClick(Scan.Intent.LnurlAuthFlow.Login(model.auth, minSuccessDelaySeconds = 1.0, keyType = keyType))
                        }
                    },
                    enabled = selectedAuthKeyType.value != null
                )
            }
            is Scan.Model.LnurlAuthFlow.LoggingIn -> {
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = annotatedStringResource(R.string.lnurl_auth_in_progress, model.auth.url.host), textAlign = TextAlign.Center)
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
                        Spacer(Modifier.height(12.dp))
                        BorderButton(text = stringResource(id = R.string.lnurl_auth_try_again_button), icon = R.drawable.ic_check, onClick = onBackClick)
                    }
                } else {
                    Card(
                        externalPadding = PaddingValues(horizontal = 16.dp),
                        internalPadding = PaddingValues(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = annotatedStringResource(id = R.string.lnurl_auth_success, model.auth.url.host), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(32.dp))
                    FilledButton(text = R.string.btn_ok, icon = R.drawable.ic_check, onClick = onBackClick)
                }
            }
        }
    }
}

@Composable
fun LnurlAuthKeyPicker(
    initialKeyType: LNUrl.Auth.KeyType,
    onAuthKeyTypeChange: (LNUrl.Auth.KeyType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initialKeyType) }
    val keyTypes = arrayListOf(LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE, LNUrl.Auth.KeyType.LEGACY_KEY_TYPE)
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        BorderButton(
            text = stringResource(
                id = when (selected) {
                    LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE -> R.string.lnurl_auth_keytype_default
                    LNUrl.Auth.KeyType.LEGACY_KEY_TYPE -> R.string.lnurl_auth_keytype_legacy
                }
            ),
            icon = R.drawable.ic_chevron_down,
            onClick = { expanded = true },
            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            space = 8.dp,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
            modifier = Modifier
        ) {
            keyTypes.forEach { keyType ->
                DropdownMenuItem(
                    onClick = {
                        selected = keyType
                        expanded = false
                        onAuthKeyTypeChange(keyType)
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(
                                when (keyType) {
                                    LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE -> R.string.lnurl_auth_keytype_default
                                    LNUrl.Auth.KeyType.LEGACY_KEY_TYPE -> R.string.lnurl_auth_keytype_legacy
                                }
                            ),
                            style = MaterialTheme.typography.body2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                when (keyType) {
                                    LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE -> R.string.lnurl_auth_keytype_default_desc
                                    LNUrl.Auth.KeyType.LEGACY_KEY_TYPE -> R.string.lnurl_auth_keytype_legacy_desc
                                }
                            ),
                            style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorResponseView(
    error: Scan.LoginError
) {
    Text(
        text = stringResource(R.string.lnurl_withdraw_error_header) + "\n" + when (error) {
            is Scan.LoginError.ServerError -> getRemoteErrorMessage(error = error.details)
            is Scan.LoginError.NetworkError -> stringResource(R.string.lnurl_auth_error_network)
            is Scan.LoginError.OtherError -> stringResource(R.string.lnurl_auth_error_other)
        },
        style = MaterialTheme.typography.body1.copy(color = negativeColor(), textAlign = TextAlign.Center),
        modifier = Modifier.padding(horizontal = 48.dp)
    )
    Spacer(Modifier.height(24.dp))
}

