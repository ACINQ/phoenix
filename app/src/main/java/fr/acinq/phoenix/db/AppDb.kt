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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PaymentMeta::class, PayToOpenMeta::class, NodeMeta::class], version = 1, exportSchema = true)
public abstract class AppDb : RoomDatabase() {
  abstract fun paymentMetaDao(): PaymentMetaDao
  abstract fun payToOpenDao(): PayToOpenMetaDao
  abstract fun nodeMetaDao(): NodeMetaDao

  companion object {
    @Volatile
    private var instance: AppDb? = null

    fun getDb(context: Context): AppDb {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "phoenix_meta_db").build().also { instance = it }
      }
    }
  }
}
