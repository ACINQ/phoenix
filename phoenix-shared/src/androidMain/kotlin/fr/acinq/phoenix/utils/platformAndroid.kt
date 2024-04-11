package fr.acinq.phoenix.utils

import android.content.Context


actual class PlatformContext(val applicationContext: Context)

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    ctx.applicationContext.filesDir.absolutePath

actual fun getDatabaseFilesDirectoryPath(ctx: PlatformContext): String? = null

actual fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String =
    ctx.applicationContext.cacheDir.absolutePath

actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    ctx.applicationContext.cacheDir.absolutePath

actual fun isMainThread(): Boolean {
    // This function is only used for iOS debugging
    return true
}
