package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eklair.utils.toByteVector32
import fr.acinq.phoenix.FakeDataStore
import fr.acinq.phoenix.ctrl.RestoreWallet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class AppRestoreWalletController(di: DI) : AppController<RestoreWallet.Model, RestoreWallet.Intent>(di, RestoreWallet.Model.Warning) {
    // TODO to be replaced by a real DB
    private val ds : FakeDataStore by instance()

    override fun process(intent: RestoreWallet.Intent) {
        when (intent) {
            RestoreWallet.Intent.AcceptWarning -> model(RestoreWallet.Model.Ready)
            is RestoreWallet.Intent.ValidateSeed -> {
                try {
                    MnemonicCode.validate(intent.mnemonics)
                    val seed = MnemonicCode.toSeed(intent.mnemonics, "")
                    ds.seed = seed.toByteVector32()
                } catch (e: IllegalArgumentException) {
                    model(RestoreWallet.Model.InvalidSeed)
                }
            }
            is RestoreWallet.Intent.FilterWordList -> when {
                intent.predicate.length > 1 -> model(
                    RestoreWallet.Model.Wordlist(
                        MnemonicCode.englishWordlist.filter { it.startsWith(intent.predicate, ignoreCase = true) }
                    )
                )
                else -> model(RestoreWallet.Model.Wordlist(emptyList()))
            }
        }
    }

}
