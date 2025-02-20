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

package fr.acinq.phoenix.android.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.extensions.isBadCertificate
import fr.acinq.phoenix.android.utils.monoTypo
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.managers.Connections
import fr.acinq.phoenix.utils.extensions.isOnion


@Composable
fun ConnectionDialog(
    connections: Connections,
    onClose: () -> Unit,
    onTorClick: () -> Unit,
    onElectrumClick: () -> Unit,
) {
    Dialog(title = stringResource(id = R.string.conndialog_title), onDismiss = onClose) {
        Column {
            if (connections.internet != Connection.ESTABLISHED) {
                Text(
                    text = stringResource(id = R.string.conndialog_network),
                    modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
                )
            } else {
                val isTorEnabled = userPrefs.getIsTorEnabled.collectAsState(initial = null).value
                val hasConnectionIssues = connections.electrum != Connection.ESTABLISHED || connections.peer != Connection.ESTABLISHED
                if (hasConnectionIssues) {
                    Text(text = stringResource(id = R.string.conndialog_summary_not_ok), Modifier.padding(horizontal = 24.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                HSeparator()
                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_electrum), connection = connections.electrum, onClick = onElectrumClick) {
                    when (val connection = connections.electrum) {
                        Connection.ESTABLISHING -> {
                            Text(text = stringResource(R.string.conndialog_connecting), style = monoTypo)
                        }
                        Connection.ESTABLISHED -> {
                            Text(text = stringResource(R.string.conndialog_connected), style = monoTypo)
                        }
                        else -> {
                            val customElectrumServer by userPrefs.getElectrumServer.collectAsState(initial = null)
                            if (isTorEnabled == true && customElectrumServer?.server?.isOnion == false && customElectrumServer?.requireOnionIfTorEnabled == true) {
                                TextWithIcon(text = stringResource(R.string.conndialog_electrum_not_onion), textStyle = monoTypo, icon = R.drawable.ic_alert_triangle, iconTint = negativeColor)
                            } else if (connection is Connection.CLOSED && connection.isBadCertificate()) {
                                TextWithIcon(text = stringResource(R.string.conndialog_closed_bad_cert), textStyle = monoTypo, icon = R.drawable.ic_alert_triangle, iconTint = negativeColor)
                            } else {
                                Text(text = stringResource(R.string.conndialog_closed), style = monoTypo)
                            }
                        }
                    }
                }
                HSeparator()
                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_lightning), connection = connections.peer)
                HSeparator()
                Spacer(Modifier.height(16.dp))


                if (hasConnectionIssues && isTorEnabled == true) {
                    Card(backgroundColor = mutedBgColor, modifier = Modifier.fillMaxWidth(), internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = onTorClick) {
                        TextWithIcon(text = stringResource(id = R.string.conndialog_tor_disclaimer_title), icon = R.drawable.ic_tor_shield, textStyle = MaterialTheme.typography.body2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stringResource(id = R.string.conndialog_tor_disclaimer_body))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDialogLine(
    label: String,
    connection: Connection?,
    onClick: (() -> Unit)? = null
) {
    ConnectionDialogLine(label = label, connection = connection, onClick = onClick) {
        Text(
            text = when (connection) {
                Connection.ESTABLISHING -> stringResource(R.string.conndialog_connecting)
                Connection.ESTABLISHED -> stringResource(R.string.conndialog_connected)
                is Connection.CLOSED, null -> stringResource(R.string.conndialog_closed)
            },
            style = monoTypo
        )
    }
}

@Composable
private fun ConnectionDialogLine(
    label: String,
    connection: Connection?,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(id = R.string.conndialog_accessibility_desc, label), onClick = onClick) else Modifier
            )
            .padding(vertical = 16.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = when (connection) {
                Connection.ESTABLISHING -> orange
                Connection.ESTABLISHED -> positiveColor
                else -> negativeColor
            },
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, modifier = Modifier.weight(1.0f))
        Spacer(modifier = Modifier.width(24.dp))
        content()
    }
}