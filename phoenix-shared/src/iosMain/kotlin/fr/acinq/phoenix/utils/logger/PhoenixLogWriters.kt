/*
 * Copyright 2024 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter
import fr.acinq.phoenix.utils.PassthruLogWriter
import fr.acinq.phoenix.utils.PlatformContext

actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> {
    return if (ctx.logger != null) {
        listOf(PassthruLogWriter(ctx))
    } else {
        listOf(OSLogWriter())
    }
}