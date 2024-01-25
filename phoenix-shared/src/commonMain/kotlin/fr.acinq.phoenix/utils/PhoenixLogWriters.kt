package fr.acinq.phoenix.utils

import co.touchlab.kermit.LogWriter

/**
 * Factory function to return a default list of LogWriters for each platform. The LogWriter is targeted at local development.
 * For production implementations, you may need to directly initialize your Logger config.
 */
expect fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter>