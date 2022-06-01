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

package fr.acinq.phoenix.android.init

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.init.RestoreWallet

@Composable
fun RestoreDisclaimerView(
    onClickNext: () -> Unit
) {
    InitScreen {
        val nc = navController
        InitHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.restore_title)
        )

        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        )
        {
            var hasCheckedWarning by rememberSaveable{ mutableStateOf(false) }
            Text(stringResource(R.string.restore_disclaimer_message))
            Row(
                Modifier.padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    hasCheckedWarning,
                    onCheckedChange = { hasCheckedWarning = it })
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.restore_disclaimer_checkbox))
            }
            BorderButton(
                text = R.string.restore_disclaimer_next,
                icon = R.drawable.ic_arrow_next,
                onClick = (onClickNext),
                enabled = hasCheckedWarning
            )

            val vm: InitViewModel =
                viewModel(factory = InitViewModel.Factory(controllerFactory, CF::initialization))
        }
    }
}