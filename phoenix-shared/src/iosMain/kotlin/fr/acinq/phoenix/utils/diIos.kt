package fr.acinq.phoenix.utils

import org.kodein.di.DI
import org.kodein.di.bindings.NoArgDIBinding
import org.kodein.di.bindings.NoArgSimpleBindingDI
import org.kodein.di.bindings.NoScope
import org.kodein.di.bindings.Scope
import org.kodein.di.provider

actual inline fun <reified T: Any> DI.Builder.screenProvider(noinline creator: NoArgSimpleBindingDI<Any>.() -> T): NoArgDIBinding<*, T> =
    provider(creator)
