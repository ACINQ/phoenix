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

package fr.acinq.eclair.phoenix

import android.app.Application
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.MBtcUnit
import fr.acinq.eclair.`MBtcUnit$`
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.eclair.phoenix.utils.Logging
import fr.acinq.eclair.phoenix.utils.Prefs
import org.slf4j.LoggerFactory

class App : Application() {

  val log = LoggerFactory.getLogger(App::class.java)

  override fun onCreate() {
    super.onCreate()
    Logging.setupLogger()
    init()
    log.info("app created")
  }

  private fun init() {
    when (Prefs.prefCoin(applicationContext)) {
      `SatUnit$`.`MODULE$` -> CoinUtils.setCoinPattern("###,###,###,##0")
      `MBtcUnit$`.`MODULE$` -> CoinUtils.setCoinPattern("###,###,###,##0.#####")
      else -> CoinUtils.setCoinPattern("###,###,###,##0.###########")
    }
  }
}
