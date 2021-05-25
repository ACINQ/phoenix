package fr.acinq.phoenix.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheTests {

    @Test
    fun testEviction() {

        val cache = Cache<String, String>(sizeLimit = 3)

        assertTrue { cache.isEmpty() }
        assertFalse { cache.isUnlimited() }

        cache["a"] = "A" // [a]
        cache["b"] = "B" // [b, a]
        cache["c"] = "C" // [c, b, a]

        assertTrue { cache.size == 3 }

        cache["d"] = "D" // [d, c, b]

        assertTrue { cache.size == 3 }
        assertTrue { cache.containsKey("a") == false }
        assertTrue { cache.containsKey("b") == true }
        assertTrue { cache.containsKey("c") == true }
        assertTrue { cache.containsKey("d") == true }

        cache["b"]       // [b, d, c]
        cache["e"] = "E" // [e, b, d]

        assertTrue { cache.size == 3 }
        assertTrue { cache.containsKey("b") == true }
        assertTrue { cache.containsKey("c") == false }
        assertTrue { cache.containsKey("d") == true }
        assertTrue { cache.containsKey("e") == true }
    }

    @Test
    fun testRemove() {

        val cache = Cache<String, String>(sizeLimit = 100)

        cache["a"] = "A" // [a]
        cache["b"] = "B" // [b, a]
        cache["c"] = "C" // [c, b, a]
        cache["d"] = "D" // [d, c, b, a]
        cache["e"] = "E" // [e, d, c, b, a]

        assertTrue { cache.size == 5 }

        cache.remove("c") // remove from middle
        assertTrue { cache.size == 4 } // [e, d, b, a]

        cache.remove("e") // remove from beginning
        assertTrue { cache.size == 3 } // [d, b, a]

        cache.remove("a") // remove from end
        assertTrue { cache.size == 2 } // [d, b]

        assertTrue { cache.containsKey("a") == false }
        assertTrue { cache.containsKey("b") == true }
        assertTrue { cache.containsKey("c") == false }
        assertTrue { cache.containsKey("d") == true }
        assertTrue { cache.containsKey("e") == false }
    }

    @Test
    fun testClear() {

        val cache = Cache<String, String>(sizeLimit = 100)

        cache["a"] = "A" // [a]
        cache["b"] = "B" // [b, a]
        cache["c"] = "C" // [c, b, a]

        assertTrue { cache.size == 3 }

        cache.clear()

        assertTrue { cache.isEmpty() }
        assertTrue { cache.size == 0 }
        assertFalse { cache.containsKey("a") }
        assertFalse { cache.containsKey("b") }
        assertFalse { cache.containsKey("c") }
    }

    @Test
    fun testTruncation() {

        val cache = Cache<String, String>(sizeLimit = 100)

        cache["a"] = "A" // [a]
        cache["b"] = "B" // [b, a]
        cache["c"] = "C" // [c, b, a]
        cache["d"] = "D" // [d, c, b, a]
        cache["e"] = "E" // [e, d, c, b, a]
        cache["f"] = "F" // [f, e, d, c, b, a]
        cache["g"] = "G" // [g, f, e, d, c, b, a]

        assertTrue { cache.size == 7 }
        cache.sizeLimit = 4 // [g, f, e, d]
        assertTrue { cache.size == 4 }

        assertTrue { cache.containsKey("a") == false }
        assertTrue { cache.containsKey("b") == false }
        assertTrue { cache.containsKey("c") == false }
        assertTrue { cache.containsKey("d") == true }
        assertTrue { cache.containsKey("e") == true }
        assertTrue { cache.containsKey("f") == true }
        assertTrue { cache.containsKey("g") == true }
    }

    @Test
    fun testBurst() {

        val cache = Cache<String, String>(sizeLimit = 3)

        cache["a"] = "A" // [a]
        cache["b"] = "B" // [b, a]
        cache["c"] = "C" // [c, b, a]
        cache["d"] = "D" // [d, c, b]

        assertTrue { cache.size == 3 }
        cache.sizeLimit = 0 // enable unlimited mode
        assertTrue { cache.size == 3 }

        cache["a"] = "A" // [a, d, c, b]
        cache["b"] = "B" // [b, a, d, c]
        cache["c"] = "C" // [c, b, a, d]
        cache["d"] = "D" // [d, c, b, a]
        cache["e"] = "E" // [e, d, c, b, a]
        cache["f"] = "F" // [f, e, d, c, b, a]
        cache["g"] = "G" // [g, f, e, d, c, b, a]

        assertTrue { cache.size == 7 }
        cache.sizeLimit = 3 // re-enable sizeLimit
        assertTrue { cache.size == 3 }

        assertTrue { cache.containsKey("a") == false }
        assertTrue { cache.containsKey("b") == false }
        assertTrue { cache.containsKey("c") == false }
        assertTrue { cache.containsKey("d") == false }
        assertTrue { cache.containsKey("e") == true }
        assertTrue { cache.containsKey("f") == true }
        assertTrue { cache.containsKey("g") == true }
    }
}