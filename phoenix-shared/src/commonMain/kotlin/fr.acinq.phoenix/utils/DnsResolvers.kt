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

package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.getValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.charsets.Charsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

enum class DnsResolvers {
    Google {
        override fun url(name: String): Pair<String, HttpRequestBuilder.() -> Unit> = "https://dns.google/resolve?name=$name&type=TXT" to {}
    },
//    Cloudflare {
//        override fun url(name: String): Pair<String, HttpRequestBuilder.() -> Unit> = "https://cloudflare-dns.com/dns-query?name=$name&type=TXT" to {
//            headers { append(HttpHeaders.Accept, "application/dns-json") }
//        }
//    },
    ;

    abstract fun url(name: String): Pair<String, HttpRequestBuilder.() -> Unit>

    suspend fun getTxtRecord(query: String): JsonObject {
        val (url, builder) = url(query)
        val response = dohClient.get(url, builder)
        return Json.decodeFromString<JsonObject>(response.bodyAsText(Charsets.UTF_8))
    }

    companion object {
        private val dohClient: HttpClient by lazy {
            HttpClient {
                install(ContentNegotiation) {
                    json(json = Json { ignoreUnknownKeys = true })
                    expectSuccess = true
                }
            }
        }

        fun getRandom(): DnsResolvers {
            return Random.nextInt(0, DnsResolvers.entries.size).let {
                DnsResolvers.entries[it]
            }
        }
    }
}

//object DnsHelper {
//

//
//    suspend fun getTXTRecord(query: String): JsonObject {
//        Random.nextInt(0, DnsResolvers.entries.size).let {
//            DnsResolvers.entries[it]
//        }.let {
//            val response = dohClient.get("$it?name=$query&type=TXT")
//            return Json.decodeFromString<JsonObject>(response.bodyAsText(Charsets.UTF_8))
//        }
//    }
//}