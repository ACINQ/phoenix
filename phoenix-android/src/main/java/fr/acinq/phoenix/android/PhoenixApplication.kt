package fr.acinq.phoenix.android

import android.app.Application
import fr.acinq.phoenix.Phoenix
import org.kodein.di.DIAware

class PhoenixApplication : Application(), DIAware {

    override val di = Phoenix().di

}
