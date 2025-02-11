/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.utils.extensions

import fr.acinq.lightning.NodeUri
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.ServerAddress

operator fun Connection?.plus(other: Connection?) : Connection =
    when {
        this == other && this != null -> this
        this == Connection.ESTABLISHING || other == Connection.ESTABLISHING -> Connection.ESTABLISHING
        this is Connection.CLOSED -> this
        other is Connection.CLOSED -> other
        else -> this ?: other ?: error("cannot combine connections [$this + $other]")
    }

val ServerAddress.isOnion get() = this.host.endsWith(".onion")
val NodeUri.isOnion get() = this.host.endsWith(".onion")