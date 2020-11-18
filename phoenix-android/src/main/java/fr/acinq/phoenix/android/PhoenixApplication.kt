package fr.acinq.phoenix.android

import android.app.Application
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.PlatformContext

class PhoenixApplication : Application() {

    val business by lazy { PhoenixBusiness(PlatformContext(this)) }

    override fun onCreate() {
        super.onCreate()

        business.start()
    }

}
