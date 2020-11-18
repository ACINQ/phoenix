package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.RecoveryPhraseConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppRecoveryPhraseConfigurationController(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager
) : AppController<RecoveryPhraseConfiguration.Model, RecoveryPhraseConfiguration.Intent>(
    loggerFactory,
    RecoveryPhraseConfiguration.Model.Awaiting
) {

    override fun process(intent: RecoveryPhraseConfiguration.Intent) {
        when (intent) {
            is RecoveryPhraseConfiguration.Intent.Decrypt -> {
                launch {
                    model(RecoveryPhraseConfiguration.Model.Decrypting)
                    val wallet = walletManager.getWallet()
                    val mnemonics: List<String> = wallet?.mnemonics ?: listOf()
                    model(RecoveryPhraseConfiguration.Model.Decrypted(mnemonics))
                }
            }
        }
    }
}