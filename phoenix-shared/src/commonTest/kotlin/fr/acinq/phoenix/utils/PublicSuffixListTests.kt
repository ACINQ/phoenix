/*
 * Copyright 2021 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import kotlin.test.Test
import kotlin.test.assertEquals

class PublicSuffixListTests {

    @Test
    fun basic() {

        // Test vectors extracted from:
        // https://publicsuffix.org/list/
        val list = """
            com
            *.jp
            // Hosts in .hokkaido.jp can"t set cookies below level 4...
            *.hokkaido.jp
            *.tokyo.jp
            // ...except hosts in pref.hokkaido.jp, which can set cookies at level 3.
            !pref.hokkaido.jp
            !metro.tokyo.jp
        """.trimIndent()

        /* The example above would be interpreted as follows, in the case of cookie-setting,
        * and using "foo" and "bar" as generic hostnames:
        *
        * - Cookies may be set for     : foo.com
        * - Cookies may be set for     : foo.bar.jp
        * - Cookies may NOT be set for : bar.jp
        * - Cookies may be set for     : foo.bar.hokkaido.jp
        * - Cookies may NOT be set for : bar.hokkaido.jp
        * - Cookies may be set for     : foo.bar.tokyo.jp
        * - Cookies may NOT be set for : bar.tokyo.jp
        * - Cookies may be set for     : pref.hokkaido.jp
        * - Cookies may be set for     : metro.tokyo.jp
        */

        val psl = PublicSuffixList(list)
        assertEquals(psl.eTldPlusOne("foo.com"), "foo.com")
        assertEquals(psl.eTldPlusOne("foo.bar.jp"), "foo.bar.jp")
        assertEquals(psl.eTldPlusOne("bar.jp"), null)
        assertEquals(psl.eTldPlusOne("foo.bar.hokkaido.jp"), "foo.bar.hokkaido.jp")
        assertEquals(psl.eTldPlusOne("bar.hokkaido.jp"), null)
        assertEquals(psl.eTldPlusOne("foo.bar.tokyo.jp"), "foo.bar.tokyo.jp")
        assertEquals(psl.eTldPlusOne("bar.tokyo.jp"), null)
        assertEquals(psl.eTldPlusOne("pref.hokkaido.jp"), "pref.hokkaido.jp")
        assertEquals(psl.eTldPlusOne("metro.tokyo.jp"), "metro.tokyo.jp")
    }

    @Test
    fun advanced() {

        // Test vectors extracted from:
        // https://raw.githubusercontent.com/publicsuffix/list/master/tests/test_psl.txt
        //
        // As referenced from:
        // https://publicsuffix.org/list/
        //
        // Notes:
        // - The test vectors are performed against the FULL list.
        // - However, the FULL list is HUGE (thousands of lines).
        // - So I trimmed the list to fit better within a unit test
        val list = """
            com
            biz
            uk.com
            ac
            *.mm
            jp
            ac.jp
            kyoto.jp
            ide.kyoto.jp
            *.kobe.jp
            !city.kobe.jp
            *.ck
            !www.ck
            us
            ak.us
            k12.ak.us
            cn
            com.cn
            公司.cn
            中国
        """.trimIndent()

        val psl = PublicSuffixList(list)

    //  val fullListSubset = mutableListOf<PublicSuffixList.Rule>()
        val check = { result: PublicSuffixList.Match, expected: String? ->
            assertEquals(result.eTld(plus = 1), expected)

    //      for (rule in result.matchingRules) {
    //          if (!fullListSubset.contains(rule)) {
    //              fullListSubset.add(rule)
    //          }
    //      }
        }

        // Mixed case.
        check(psl.match("COM"), null)
        check(psl.match("example.COM"), "example.com")
        check(psl.match("WwW.example.COM"), "example.com")
        // Leading dot.
        // ACINQ Note: Puposefully excluding these tests.
        // - it's not the job of PublicSuffixList class to ensure given string is a proper domain
        // - that responsibility lies elsewhere (existing URL utilities should handle it)
//      check(psl.match(".com"), null)
//      check(psl.match(".example"), null)
//      check(psl.match(".example.com"), null)
//      check(psl.match(".example.example"), null)
        // Unlisted TLD.
        check(psl.match("example"), null)
        check(psl.match("example.example"), "example.example")
        check(psl.match("b.example.example"), "example.example")
        check(psl.match("a.b.example.example"), "example.example")
        // TLD with only 1 rule.
        check(psl.match("biz"), null)
        check(psl.match("domain.biz"), "domain.biz")
        check(psl.match("b.domain.biz"), "domain.biz")
        check(psl.match("a.b.domain.biz"), "domain.biz")
        // TLD with some 2-level rules.
        check(psl.match("com"), null)
        check(psl.match("example.com"), "example.com")
        check(psl.match("b.example.com"), "example.com")
        check(psl.match("a.b.example.com"), "example.com")
        check(psl.match("uk.com"), null)
        check(psl.match("example.uk.com"), "example.uk.com")
        check(psl.match("b.example.uk.com"), "example.uk.com")
        check(psl.match("a.b.example.uk.com"), "example.uk.com")
        check(psl.match("test.ac"), "test.ac")
        // TLD with only 1 (wildcard) rule.
        check(psl.match("mm"), null)
        check(psl.match("c.mm"), null)
        check(psl.match("b.c.mm"), "b.c.mm")
        check(psl.match("a.b.c.mm"), "b.c.mm")
        // More complex TLD.
        check(psl.match("jp"), null)
        check(psl.match("test.jp"), "test.jp")
        check(psl.match("www.test.jp"), "test.jp")
        check(psl.match("ac.jp"), null)
        check(psl.match("test.ac.jp"), "test.ac.jp")
        check(psl.match("www.test.ac.jp"), "test.ac.jp")
        check(psl.match("kyoto.jp"), null)
        check(psl.match("test.kyoto.jp"), "test.kyoto.jp")
        check(psl.match("ide.kyoto.jp"), null)
        check(psl.match("b.ide.kyoto.jp"), "b.ide.kyoto.jp")
        check(psl.match("a.b.ide.kyoto.jp"), "b.ide.kyoto.jp")
        check(psl.match("c.kobe.jp"), null)
        check(psl.match("b.c.kobe.jp"), "b.c.kobe.jp")
        check(psl.match("a.b.c.kobe.jp"), "b.c.kobe.jp")
        check(psl.match("city.kobe.jp"), "city.kobe.jp")
        check(psl.match("www.city.kobe.jp"), "city.kobe.jp")
        // TLD with a wildcard rule and exceptions.
        check(psl.match("ck"), null)
        check(psl.match("test.ck"), null)
        check(psl.match("b.test.ck"), "b.test.ck")
        check(psl.match("a.b.test.ck"), "b.test.ck")
        check(psl.match("www.ck"), "www.ck")
        check(psl.match("www.www.ck"), "www.ck")
        // US K12.
        check(psl.match("us"), null)
        check(psl.match("test.us"), "test.us")
        check(psl.match("www.test.us"), "test.us")
        check(psl.match("ak.us"), null)
        check(psl.match("test.ak.us"), "test.ak.us")
        check(psl.match("www.test.ak.us"), "test.ak.us")
        check(psl.match("k12.ak.us"), null)
        check(psl.match("test.k12.ak.us"), "test.k12.ak.us")
        check(psl.match("www.test.k12.ak.us"), "test.k12.ak.us")
        // IDN labels.
        check(psl.match("食狮.com.cn"), "食狮.com.cn")
        check(psl.match("食狮.公司.cn"), "食狮.公司.cn")
        check(psl.match("www.食狮.公司.cn"), "食狮.公司.cn")
        check(psl.match("shishi.公司.cn"), "shishi.公司.cn")
        check(psl.match("公司.cn"), null)
        check(psl.match("食狮.中国"), "食狮.中国")
        check(psl.match("www.食狮.中国"), "食狮.中国")
        check(psl.match("shishi.中国"), "shishi.中国")
        check(psl.match("中国"), null)
        // Same as above, but punycoded.
        // ACINQ Note: Excluding these tests.
        // - we don't have kotlin native tools to perform IDN
        // - it does exist in java.net.IDN though
//      check(psl.match("xn--85x722f.com.cn"), "xn--85x722f.com.cn")
//      check(psl.match("xn--85x722f.xn--55qx5d.cn"), "xn--85x722f.xn--55qx5d.cn")
//      check(psl.match("www.xn--85x722f.xn--55qx5d.cn"), "xn--85x722f.xn--55qx5d.cn")
//      check(psl.match("shishi.xn--55qx5d.cn"), "shishi.xn--55qx5d.cn")
//      check(psl.match("xn--55qx5d.cn"), null)
//      check(psl.match("xn--85x722f.xn--fiqs8s"), "xn--85x722f.xn--fiqs8s")
//      check(psl.match("www.xn--85x722f.xn--fiqs8s"), "xn--85x722f.xn--fiqs8s")
//      check(psl.match("shishi.xn--fiqs8s"), "shishi.xn--fiqs8s")
//      check(psl.match("xn--fiqs8s"), null)

    //  println("fullListSubset:")
    //  for (rule in fullListSubset) {
    //      println("${fullListSubset.label}")
    //  }
    }
}