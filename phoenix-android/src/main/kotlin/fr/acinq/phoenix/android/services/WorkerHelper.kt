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

package fr.acinq.phoenix.android.services

import android.content.Context
import fr.acinq.bitcoin.TxId
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.utils.MnemonicLanguage
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.flow.first

object WorkerHelper {
    suspend fun startIsolatedBusiness(context: Context, encryptedSeed: EncryptedSeed.V2.NoAuth, userPrefs: UserPrefsRepository): PhoenixBusiness {
        val mnemonics = encryptedSeed.decrypt()

        // retrieve preferences before starting business
        val business = PhoenixBusiness(PlatformContext(context))
        val electrumServer = userPrefs.getElectrumServer.first()
        val isTorEnabled = userPrefs.getIsTorEnabled.first()
        val liquidityPolicy = userPrefs.getLiquidityPolicy.first()
        val trustedSwapInTxs = LegacyPrefsDatastore.getMigrationTrustedSwapInTxs(context).first()
        val preferredFiatCurrency = userPrefs.getFiatCurrency.first()

        // preparing business
        val seed = business.walletManager.mnemonicsToSeed(EncryptedSeed.toMnemonics(mnemonics), wordList = MnemonicLanguage.English.wordlist())
        business.walletManager.loadWallet(seed)
        business.appConfigurationManager.updateElectrumConfig(electrumServer)
        business.appConfigurationManager.updatePreferredFiatCurrencies(
            AppConfigurationManager.PreferredFiatCurrencies(primary = preferredFiatCurrency, others = emptySet())
        )

        // start business
        business.start(
            StartupParams(
                requestCheckLegacyChannels = false,
                isTorEnabled = isTorEnabled,
                liquidityPolicy = liquidityPolicy,
                trustedSwapInTxs = trustedSwapInTxs.map { TxId(it) }.toSet()
            )
        )

        // start the swap-in wallet watcher
        business.peerManager.getPeer().startWatchSwapInWallet()
        return business
    }
}