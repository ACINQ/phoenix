package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eklair.utils.secure
import fr.acinq.eklair.utils.toByteVector32
import fr.acinq.phoenix.FakeDataStore
import fr.acinq.phoenix.ctrl.Init
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppInitController(di: DI) : AppController<Init.Model, Init.Intent>(di, Init.Model.Initialization) {
    // TODO to be replaced by a real DB
    private val ds: FakeDataStore by instance()

    override fun process(intent: Init.Intent) = when (intent) {
        Init.Intent.CreateWallet -> {
            model(Init.Model.Creating)
            val words = MnemonicCode.toMnemonics(Random.secure().nextBytes(16))
            ds.seed = MnemonicCode.toSeed(words, "").toByteVector32()
        }
    }

}
