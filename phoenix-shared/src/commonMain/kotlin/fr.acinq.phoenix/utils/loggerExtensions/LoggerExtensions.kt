package fr.acinq.phoenix.utils.loggerExtensions

import co.touchlab.kermit.Logger
import kotlin.jvm.JvmOverloads

fun Logger.appendingTag(tag: String): Logger {
    return withTag("${this.tag}.${tag}")
}

@JvmOverloads
inline fun Logger.verbose(throwable: Throwable? = null, tag: String = this.tag, message: () -> String){
    this.v(throwable, tag, message)
}

@JvmOverloads
inline fun Logger.debug(throwable: Throwable? = null, tag: String = this.tag, message: () -> String){
    this.d(throwable, tag, message)
}

@JvmOverloads
inline fun Logger.info(throwable: Throwable? = null, tag: String = this.tag, message: () -> String){
    this.i(throwable, tag, message)
}

@JvmOverloads
inline fun Logger.warning(throwable: Throwable? = null, tag: String = this.tag, message: () -> String){
    this.w(throwable, tag, message)
}

@JvmOverloads
inline fun Logger.error(throwable: Throwable? = null, tag: String = this.tag, message: () -> String){
    this.e(throwable, tag, message)
}

@JvmOverloads
inline fun Logger.always(throwable: Throwable? = null, tag: String = this.tag, message: () -> String){
    this.a(throwable, tag, message)
}

@JvmOverloads
inline fun Logger.verbose(messageString: String, throwable: Throwable? = null, tag: String = this.tag){
    this.v(messageString, throwable, tag)
}

@JvmOverloads
inline fun Logger.debug(messageString: String, throwable: Throwable? = null, tag: String = this.tag){
    this.d(messageString, throwable, tag)
}

@JvmOverloads
inline fun Logger.info(messageString: String, throwable: Throwable? = null, tag: String = this.tag){
    this.i(messageString, throwable, tag)
}

@JvmOverloads
inline fun Logger.warning(messageString: String, throwable: Throwable? = null, tag: String = this.tag){
    this.w(messageString, throwable, tag)
}

@JvmOverloads
inline fun Logger.error(messageString: String, throwable: Throwable? = null, tag: String = this.tag){
    this.e(messageString, throwable, tag)
}

@JvmOverloads
inline fun Logger.always(messageString: String, throwable: Throwable? = null, tag: String = this.tag){
    this.a(messageString, throwable, tag)
}