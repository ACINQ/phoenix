package fr.acinq.phoenix.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ContextAmbient
import androidx.ui.tooling.preview.PreviewActivity
import fr.acinq.phoenix.android.PhoenixApplication
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import org.kodein.log.newLogger


@Composable
fun logger(): Logger {
    val context = ContextAmbient.current
    val application = context.applicationContext

    if (application !is PhoenixApplication) { // Preview mode
        return remember { LoggerFactory(simplePrintFrontend).newLogger(context::class) }
    }

    return remember {
        application.business.loggerFactory.newLogger(context::class)
    }
}
