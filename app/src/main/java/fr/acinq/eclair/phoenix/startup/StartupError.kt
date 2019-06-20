/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.phoenix.startup

import android.content.Context
import fr.acinq.eclair.phoenix.R

enum class StartupError {
  ERROR_WRONG_PWD,
  ERROR_UNREADABLE,
  ERROR_NETWORK,
  ERROR_GENERIC;

  fun toto(): String {
    return "aiohaoidh"
  }

  companion object {
    fun getMessage(context: Context, code: StartupError): String {
      return when (code) {
        ERROR_WRONG_PWD  -> context.resources.getString(R.string.startup_error_wrong_pwd)
        ERROR_UNREADABLE -> context.resources.getString(R.string.startup_error_unreadable)
        ERROR_NETWORK    -> context.resources.getString(R.string.startup_error_network)
        else             -> context.resources.getString(R.string.startup_error_generic)
      }
    }
  }
}
