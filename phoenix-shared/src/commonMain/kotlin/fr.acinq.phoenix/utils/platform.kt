package fr.acinq.phoenix.utils


expect class PlatformContext

expect fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String
expect fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String
expect fun getTemporaryDirectoryPath(ctx: PlatformContext): String
