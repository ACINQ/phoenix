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

package fr.acinq.phoenix.db

import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import androidx.room.*
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Wallet
import okhttp3.Request
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlin.math.abs


@Entity(tableName = "node_meta")
data class NodeMeta(
  @PrimaryKey @ColumnInfo(name = "pub_key") val pub_key: String,
  @ColumnInfo(name = "alias") val alias: String,
  @ColumnInfo(name = "update_timestamp") val timestamp: Long,
  @ColumnInfo(name = "custom_alias") val customAlias: String? = null
)

@Dao
interface NodeMetaDao {
  @WorkerThread
  @Query("SELECT * from node_meta WHERE pub_key=:id")
  fun get(id: String): NodeMeta?

  @WorkerThread
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insert(data: NodeMeta)
}

class NodeMetaRepository private constructor(private val dao: NodeMetaDao) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /** Get the node metadata from db. If meta is too old (15 days) or does not exist, query 1ML and add to DB. */
  @WorkerThread
  suspend fun get(id: String): NodeMeta? {
    val nodeMeta = dao.get(id)
    return if (nodeMeta == null || abs(System.currentTimeMillis() - nodeMeta.timestamp) > DateUtils.DAY_IN_MILLIS * 15) {
      Wallet.httpClient.newCall(
        Request.Builder().url("${Constants.ONEML_URL}/node/$id/json").build()
      ).execute().run {
        val body = body()
        if (!isSuccessful || body == null) {
          null
        } else {
          val alias = JSONObject(body.string()).getString("alias")
          val meta = NodeMeta(id, alias, System.currentTimeMillis())
          log.debug("got 1ML meta=$meta")
          insert(meta)
          body.close()
          meta
        }
      }
    } else {
      nodeMeta
    }
  }

  suspend fun insert(meta: NodeMeta) = dao.insert(meta)

  companion object {
    @Volatile
    private var instance: NodeMetaRepository? = null

    fun getInstance(dao: NodeMetaDao) =
      instance ?: synchronized(this) {
        instance ?: NodeMetaRepository(dao).also { instance = it }
      }
  }
}
