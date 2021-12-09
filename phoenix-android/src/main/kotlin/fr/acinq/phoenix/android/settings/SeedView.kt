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


import android.content.Context
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.ScreenBody
import fr.acinq.phoenix.android.components.ScreenHeader
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.utils.Converter
import fr.acinq.phoenix.android.utils.logger

@Composable
fun SeedView(appVM: AppViewModel) {
    val log = logger("SeedView")
    val nc = navController
    val showSeedDialog = remember { mutableStateOf(false) }
    ScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.displayseed_title))
    ScreenBody {
        AndroidView(factory = {
            TextView(it).apply {
                text = Converter.html(it.getString(R.string.displayseed_instructions))
            }
        })
        if (showSeedDialog.value) {
            SeedDialog(onClose = { showSeedDialog.value = false }, appVM = appVM)
        }
        Spacer(modifier = Modifier.height(16.dp))
        BorderButton(onClick = { showSeedDialog.value = true }, text = R.string.displayseed_authenticate_button, icon = R.drawable.ic_key)
    }
}

@Composable
fun SeedDialog(onClose: () -> Unit, appVM: AppViewModel) {
    val context = LocalContext.current
    val seed = appVM.decryptSeed(context)
    Dialog(
        onDismiss = onClose
    ) {
        if (seed != null) {
            // TODO: suspend method with state
            val words = EncryptedSeed.toMnemonics(seed)
            Column(Modifier.padding(24.dp)) {
                Text(text = stringResource(id = R.string.displayseed_dialog_header))
                Spacer(modifier = Modifier.height(16.dp))
                AndroidView(factory = { ctx ->
                    TableLayout(ctx).apply {
                        var i = 0
                        while (i < words.size / 2) {
                            addView(TableRow(context).apply {
                                this.gravity = Gravity.CENTER
                                this.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                                this.addView(buildWordView(ctx, i, words[i], true))
                                this.addView(buildWordView(ctx, i + words.size / 2, words[i + (words.size / 2)], false))
                            })
                            i += 1
                        }
                    }
                })
            }
        } else {
            Text(stringResource(id = R.string.displayseed_error_generic))
        }
    }
}

private fun buildWordView(context: Context, i: Int, word: String, hasRightPadding: Boolean): TextView {
    val bottomPadding = context.resources.getDimensionPixelSize(R.dimen.space_xxs)
    val rightPadding = if (hasRightPadding) context.resources.getDimensionPixelSize(R.dimen.space_lg) else 0
    val textView = TextView(context)
    textView.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
    textView.text = Converter.html(context.getString(R.string.displayseed_words_td, i + 1, word))
    textView.setPadding(0, 0, rightPadding, bottomPadding)
    return textView
}
