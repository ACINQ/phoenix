package fr.acinq.phoenix.utils

import android.app.Application


actual class PlatformContext(val application: Application)

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    ctx.application.filesDir.absolutePath

actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    ctx.application.cacheDir.absolutePath
