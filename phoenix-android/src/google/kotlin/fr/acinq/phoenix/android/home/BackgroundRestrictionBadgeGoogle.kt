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

package fr.acinq.phoenix.android.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.utils.warningColor

@Composable
fun BackgroundRestrictionBadge(
    appViewModel: AppViewModel,
    isPowerSaverMode: Boolean,
    isTorEnabled: Boolean,
) {
    val context = LocalContext.current
    val isFcmAvailable = appViewModel.service?.isFcmAvailable(context) == true

    if (isTorEnabled || isPowerSaverMode || !isFcmAvailable) {
        var showDialog by remember { mutableStateOf(false) }

        TopBadgeButton(
            text = null,
            icon = R.drawable.ic_alert_triangle,
            iconTint = warningColor,
            onClick = { showDialog = true },
        )
        Spacer(modifier = Modifier.width(4.dp))

        if (showDialog) {
            Dialog(
                onDismiss = { showDialog = false },
                title = stringResource(id = R.string.home_background_restriction_title)
            ) {
                Text(
                    text = stringResource(id = R.string.home_background_restriction_body_1),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isTorEnabled) {
                    TextWithIcon(
                        text = stringResource(id = R.string.home_background_restriction_tor),
                        textStyle = MaterialTheme.typography.body2,
                        icon = R.drawable.ic_tor_shield_ok,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }

                if (isPowerSaverMode) {
                    TextWithIcon(
                        text = stringResource(id = R.string.home_background_restriction_powersaver),
                        textStyle = MaterialTheme.typography.body2,
                        icon = R.drawable.ic_battery_charging,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }

                if (!isFcmAvailable) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                        TextWithIcon(
                            text = stringResource(id = R.string.home_background_restriction_fcm),
                            textStyle = MaterialTheme.typography.body2,
                            icon = R.drawable.ic_battery_charging,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(id = R.string.home_background_restriction_fcm_details),
                            style = MaterialTheme.typography.subtitle2,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
            }
        }
    }
}