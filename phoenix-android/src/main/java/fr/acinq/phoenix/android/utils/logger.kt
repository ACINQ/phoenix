package fr.acinq.phoenix.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ContextAmbient
import fr.acinq.phoenix.android.PhoenixApplication
import org.kodein.di.android.closestDI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import org.kodein.log.newLogger


@Composable
fun logger(): Logger {
    val context = ContextAmbient.current

    if (context.applicationContext !is PhoenixApplication) { // Preview mode
        return remember { LoggerFactory(simplePrintFrontend).newLogger(context::class) }
    }

    return rememberWithDI { di ->
        val factory = di.direct.instance<LoggerFactory>()

        factory.newLogger(context::class)
    }
}
