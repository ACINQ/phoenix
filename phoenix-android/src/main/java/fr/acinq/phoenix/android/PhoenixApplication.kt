package fr.acinq.phoenix.android

import android.app.Application
import android.content.Context
import fr.acinq.phoenix.PhoenixBusiness
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bind
import org.kodein.di.instance

class PhoenixApplication : Application(), DIAware {

    override val di = DI.lazy {
        importAll(PhoenixBusiness.diModules)
        bind<Context>(tag = "app") with instance(this@PhoenixApplication)
    }

}
