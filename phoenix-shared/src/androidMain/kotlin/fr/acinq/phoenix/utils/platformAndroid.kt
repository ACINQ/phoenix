package fr.acinq.phoenix.utils

import android.content.Context


actual class PlatformContext(val applicationContext: Context)

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    ctx.applicationContext.filesDir.absolutePath

actual fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String =
    ctx.applicationContext.cacheDir.absolutePath

actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    ctx.applicationContext.cacheDir.absolutePath
