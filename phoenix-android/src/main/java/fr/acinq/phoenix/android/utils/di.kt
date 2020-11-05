package fr.acinq.phoenix.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ContextAmbient
import org.kodein.di.DI
import org.kodein.di.DIProperty
import org.kodein.di.android.DIPropertyDelegateProvider
import org.kodein.di.android.closestDI


/**
 * Gets the closest DI container.
 *
 * Usually [fr.acinq.phoenix.android.PhoenixApplication.di], unless layered DI is used.
 * See https://docs.kodein.org/kodein-di/7.2.0/framework/android.html#_layered_dependencies
 */
@Composable
fun di(): DI {
    val context = ContextAmbient.current
    val di by remember<DIPropertyDelegateProvider<Any?>> { closestDI(context) }
    return di
}

@Composable
fun <T> rememberWithDI(calculation: (DI) -> T): T {
    val di = di()
    return remember { calculation(di) }
}
