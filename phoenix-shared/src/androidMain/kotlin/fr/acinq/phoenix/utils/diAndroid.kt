package fr.acinq.phoenix.utils

import org.kodein.di.DI
import org.kodein.di.android.ActivityRetainedScope
import org.kodein.di.bindings.NoArgBindingDI
import org.kodein.di.bindings.NoArgDIBinding
import org.kodein.di.scoped
import org.kodein.di.singleton

actual inline fun <reified T: Any> DI.Builder.screenProvider(noinline creator: NoArgBindingDI<Any>.() -> T): NoArgDIBinding<*, T> =
    scoped(ActivityRetainedScope).singleton(creator = creator)
