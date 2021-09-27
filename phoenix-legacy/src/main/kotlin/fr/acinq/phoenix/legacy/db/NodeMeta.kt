/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.legacy.db

import android.text.format.DateUtils
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.utils.Wallet
import okhttp3.Request
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlin.math.abs


class NodeMetaRepository private constructor(private val queries: NodeMetaQueries) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun get(pubKey: String): NodeMeta? {
    val nodeMeta = queries.get(pubKey).executeAsOneOrNull()
    return if (nodeMeta == null || abs(System.currentTimeMillis() - nodeMeta.update_timestamp) > DateUtils.DAY_IN_MILLIS * 15) {
      Wallet.httpClient.newCall(
        Request.Builder().url("${Constants.ONEML_URL}/node/$pubKey/json").build()
      ).execute().run {
        val body = body()
        if (!isSuccessful || body == null) {
          null
        } else {
          val alias = JSONObject(body.string()).getString("alias")
          log.debug("got 1ML alias=$alias for id=$pubKey")
          queries.insert(pubKey, alias, System.currentTimeMillis())
          body.close()
          queries.get(pubKey).executeAsOneOrNull()
        }
      }
    } else {
      nodeMeta
    }
  }

  companion object {
    @Volatile
    private var instance: NodeMetaRepository? = null

    fun getInstance(queries: NodeMetaQueries) =
      instance ?: synchronized(this) {
        instance ?: NodeMetaRepository(queries).also { instance = it }
      }
  }
}
