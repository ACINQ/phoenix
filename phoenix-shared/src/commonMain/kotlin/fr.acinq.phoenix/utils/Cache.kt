package fr.acinq.phoenix.utils

/**
 * The Cache implements a simple strict cache.
 *
 * It monitors usage (both gets & puts) so that eviction is properly ordered.
 *
 * For example:
 * If you set a sizeLimit of 4, then when you add the 5th item to the cache,
 * another item is automatically evicted.
 *
 * Which item gets evicted depends entirely on usage.
 * The Cache maintains a doubly linked-list of items ordered by access.
 * The most recently accessed item is at the front of the linked-list,
 * and the least recently accessed item is at the back.
 * So it's very quick and efficient to evict items based on recent usage.
 * It's also efficient to update the linked-list during usage.
 */
class Cache<Key, Value>(sizeLimit: Int) {

    private class CacheItem<Key, Value>(
        var key: Key,
        var value: Value
    ) {
        var prev: CacheItem<Key, Value>? = null // linked-list pointer
        var next: CacheItem<Key, Value>? = null // linked-list pointer
    }

    private var _sizeLimit: Int
    private var _map: MutableMap<Key, CacheItem<Key, Value>>

    init {
        _sizeLimit = sizeLimit
        _map = mutableMapOf()
    }

    // Pointers to front & back of linked-list
    private var mostRecentCacheItem: CacheItem<Key, Value>? = null
    private var leastRecentCacheItem: CacheItem<Key, Value>? = null

    val size: Int get() = _map.size

    var sizeLimit: Int
        get() = _sizeLimit
        set(newValue) {

            if (_sizeLimit == newValue) {
                return // no changes
            }

            _sizeLimit = newValue
            if (_sizeLimit > 0) {

                while (_map.size > _sizeLimit) {

                    // To get to this code branch:
                    // - _sizeLimit > 0
                    // - _map.size > _sizeLimit
                    //
                    // Thus: _map.size > 1, meaning:
                    // - leastRecentCacheItem is non-null
                    // - leastRecentCacheItem.prev is non-null

                    val keyToEvict = leastRecentCacheItem!!.key

                    leastRecentCacheItem = leastRecentCacheItem?.prev
                    leastRecentCacheItem?.next = null

                    _map.remove(keyToEvict)
                }
            }
        } // </var sizeLimit: Int>

    fun isUnlimited(): Boolean = _sizeLimit <= 0
    fun isEmpty(): Boolean = _map.isEmpty()
    fun containsKey(key: Key): Boolean = _map.containsKey(key)

    operator fun get(key: Key): Value? {

        return _map[key]?.let { item ->

            if (item !== mostRecentCacheItem) {

                // Remove item from current position in linked-list.
                //
                // Since item is non-null,
                // we know there's a valid mostRecentCacheItem & leastRecentCacheItem.

                item.prev?.next = item.next

                if (item === leastRecentCacheItem) {
                    // We know the item is NOT the mostRecentCacheItem.
                    // We know the item IS the leastRecentCacheItem.
                    // Thus: there are at least 2 items in the list

                    leastRecentCacheItem = item.prev

                } else {
                    // We know the item is NOT the mostRecentCacheItem.
                    // We know the item is NOT the leastRecentCacheItem.
                    // Thus: there are at least 3 items in the list

                    item.next?.prev = item.prev
                }

                // Move item to beginning of linked-list

                item.prev = null
                item.next = mostRecentCacheItem

                mostRecentCacheItem?.prev = item
                mostRecentCacheItem = item
            }

            item.value
        }
    }

    operator fun set(key: Key, value: Value): Unit {

        val existingItem = _map[key]
        if (existingItem != null) {

            // Update item value
            existingItem.value = value

            if (existingItem !== mostRecentCacheItem) {

                // Remove item from current position in linked-list
                //
                // Notes:
                // We fetched the item from the list,
                // so we know there's a valid mostRecentCacheItem & leastRecentCacheItem.
                // Furthermore, we know the item isn't the mostRecentCacheItem.

                existingItem.prev?.next = existingItem.next

                if (existingItem === leastRecentCacheItem) {
                    // We know the item is NOT the mostRecentCacheItem.
                    // We know the item IS the leastRecentCacheItem.
                    // Thus: there are at least 2 items in the list

                    leastRecentCacheItem = existingItem.prev
                } else {
                    // We know the item is NOT the mostRecentCacheItem.
                    // We know the item is NOT the leastRecentCacheItem.
                    // Thus: there are at least 3 items in the list

                    existingItem.next?.prev = existingItem.prev
                }

                // Move item to beginning of linked-list

                existingItem.prev = null
                existingItem.next = mostRecentCacheItem

                mostRecentCacheItem?.prev = existingItem
                mostRecentCacheItem = existingItem
            }

        } else { // existingItem == null

            val newItem = CacheItem(key, value)
            _map[key] = newItem

            // Add item to beginning of linked-list

            newItem.next = mostRecentCacheItem

            mostRecentCacheItem?.prev = newItem
            mostRecentCacheItem = newItem

            // Evict leastRecentCacheItem if needed

            if ((_sizeLimit > 0) && (_map.size > _sizeLimit)) {

                val keyToEvict = leastRecentCacheItem!!.key

                leastRecentCacheItem = leastRecentCacheItem?.prev
                leastRecentCacheItem?.next = null

                _map.remove(keyToEvict)

            } else {

                if (leastRecentCacheItem == null) {
                    // There is only 1 item in list.
                    // leastRecentCacheItem === mostRecentCacheItem === newItem
                    leastRecentCacheItem = newItem
                }
            }
        }
    }

    fun remove(key: Key): Unit {

        _map[key]?.let { item ->

            if (mostRecentCacheItem === item) {
                mostRecentCacheItem = item.next
            } else {
                item.prev?.next = item.next
            }

            if (leastRecentCacheItem === item) {
                leastRecentCacheItem = item.prev
            } else {
                item.next?.prev = item.prev
            }

            _map.remove(key)
        }
    }

    fun clear(): Unit {

        var item = leastRecentCacheItem
        while (item != null) {

            val prev = item.prev
            item.next = null
            item.prev = null
            item = prev
        }

        leastRecentCacheItem = null
        mostRecentCacheItem = null
        _map.clear()
    }

    fun filteredKeys(
        isIncluded: (Key) -> Boolean
    ): List<Key> {

        var results = mutableListOf<Key>()

        var item = mostRecentCacheItem
        while (item != null) {
            if (isIncluded(item.key)) {
                results.add(item.key)
            }

            item = item.next
        }

        return results
    }
}
