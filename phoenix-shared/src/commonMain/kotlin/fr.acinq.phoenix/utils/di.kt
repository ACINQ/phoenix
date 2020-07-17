package fr.acinq.phoenix.utils

import org.kodein.di.DI
import org.kodein.di.bindings.NoArgDIBinding
import org.kodein.di.bindings.NoArgSimpleBindingDI


// Will be part of Kodein-DI in a future version (when first class support for iOS is released).
// Ensures that the same screen will always receive the same controller.
// I.e, in Android, the controller survives activity restarts.
expect inline fun <reified T: Any> DI.Builder.screenProvider(noinline creator: NoArgSimpleBindingDI<Any>.() -> T): NoArgDIBinding<*, T>
