/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.data.lnurl

import fr.acinq.bitcoin.Bech32
import fr.acinq.phoenix.data.LNUrl
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LNUrlBaseTest {

    private fun encode(source: String) = Bech32.encode(
        hrp = "lnurl",
        int5s = Bech32.eight2five(source.encodeToByteArray()).toByteArray(),
        encoding = Bech32.Encoding.Bech32
    )

    @Test
    fun parseBech32Url() {
        // https://service.com/api?q=3fc3645b439ce8e7f2553a69e5267081d96dcd340693afabe04be7b0ccd178df
        val source = "LNURL1DP68GURN8GHJ7UM9WFMXJCM99E3K7MF0V9CXJ0M385EKVCENXC6R2C35XVUKXEFCV5MKVV34X5EKZD3EV56NYD3HXQURZEPEXEJXXEPNXSCRVWFNV9NXZCN9XQ6XYEFHVGCXXCMYXYMNSERXFQ5FNS"
        val url = LNUrl.parseBech32Url(source)
        assertTrue { url.protocol.isSecure() }
        assertEquals("service.com", url.host)
        assertEquals("/api", url.encodedPath)
        assertEquals(parametersOf("q", "3fc3645b439ce8e7f2553a69e5267081d96dcd340693afabe04be7b0ccd178df"), url.parameters)
    }

    @Test
    fun parseBech32Url_unsafe() {
        val source = encode("http://service.com/api?foo=whatever=loremipsum")
        assertFailsWith(LNUrl.Error.UnsafeCallback::class) { LNUrl.parseBech32Url(source) }
    }

    @Test
    fun parseNonBech32() {
        val source = "lnurlp:service.com/whatever?param1=32159ab"
        val url = LNUrl.parseNonBech32Url(source)
        assertEquals(URLProtocol.HTTPS, url.protocol)
        assertEquals("service.com", url.host)
        assertEquals("/whatever", url.encodedPath)
    }
}