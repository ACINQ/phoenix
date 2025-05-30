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

package fr.acinq.phoenix.db

import fr.acinq.phoenix.utils.PlatformContext
import okio.Path
import kotlin.collections.List

actual abstract class UsingContextTest {
    actual fun getPlatformContext(): PlatformContext = PlatformContext()
    actual fun setUpDatabase(context: PlatformContext, databasePaths: List<Path>) {
        TODO()
    }
}
