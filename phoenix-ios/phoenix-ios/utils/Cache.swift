import Foundation

/// The Cache implements a simple strict cache.
///
/// It is similar to NSCache and shares a similar API.
/// However, this Cache implements a strict countLimit and monitors usage so eviction is properly ordered.
///
/// For example:
/// If you set a countLimit of 4, then when you add the 5th item to the cache,
/// another item is automatically evicted. It doesn't happen at a later time as with NSCache.
/// It happens atomically during the addition of the 5th item.
///
/// Which item gets evicted depends entirely on usage.
/// The Cache maintains a doubly linked-list of items ordered by access.
/// The most recently accessed item is at the front of the linked-list,
/// and the least recently accessed item is at the back.
/// So it's very quick and efficient to evict items based on recent usage.
///
/// This class is NOT thread-safe.
/// This is because it's designed specifically for performance.
/// Be sure to always use it from the same thread, or from within the same serial dispatch queue.
///
/// Legal Note:
/// This class is based on YapCache (from the YapDatabase project).
/// This class and YapCache share the same author.
/// Permission for derivative work is hereby granted.
///
class Cache<Key: Hashable, Value: Any> {
	
	private class CacheItem<Value: Any> {
		
		unowned var prev: CacheItem<Value>? = nil // linked-list pointer
		unowned var next: CacheItem<Value>? = nil // linked-list pointer
		
		var key: Key
		var value: Value
		
		init(key: Key, value: Value) {
			self.key = key
			self.value = value
		}
	}
	
	private var _countLimit: Int
	private var _dict: [Key: CacheItem<Value>]
	
	private unowned var mostRecentCacheItem: CacheItem<Value>? = nil
	private unowned var leastRecentCacheItem: CacheItem<Value>? = nil
	
	#if Cache_Enable_Statistics
	private(set) var hitCount: UInt64 = 0
	private(set) var missCount: UInt64 = 0
	private(set) var evictionCount: UInt64 = 0
	#endif
	
	/// Initialized the cache with the given countLimit.
	/// When the countLimit is reached, items are automatically evicted based on usage patterns.
	///
	/// If you provide a non-positive countLimit, then there is no limit enforced.
	///
	init(countLimit: Int) {
		
		// zero (or a negative value) is a valid countLimit (it means unlimited)
		_countLimit = countLimit
		_dict = Dictionary(minimumCapacity: _countLimit)
	}
	
	var countLimit: Int {
		get {
			return _countLimit
		}
		set(newValue) {
			guard _countLimit != newValue else {
				return // no changes
			}
			
			_countLimit = newValue
			if _countLimit > 0 {
				
				while _dict.count > countLimit {
					
					// To get to this code branch:
					// - _countLimit > 0
					// - _dict.count > _countLimit
					//
					// Thus: _dict.count >= 2, meaning:
					// - leastRecentCacheItem is non-nil
					// - leastRecentCacheItem.prev is non-nil
					
					let keyToEvict = leastRecentCacheItem!.key
					
					leastRecentCacheItem = leastRecentCacheItem?.prev
					leastRecentCacheItem?.next = nil
						
					_dict[keyToEvict] = nil
					
					#if Cache_Enable_Statistics
					evictionCount += 1
					#endif
				}
			}
		}
	}
	
	var isUnlimited: Bool {
		return _countLimit <= 0
	}
	
	subscript(key: Key) -> Value? {
		
		get {
			
			if let item = _dict[key] {
				
				if item !== mostRecentCacheItem {
					
					// Remove item from current position in linked-list.
					//
					// Notes:
					// We fetched the item from the list,
					// so we know there's a valid mostRecentCacheItem & leastRecentCacheItem.
					
					item.prev?.next = item.next
					
					if item === leastRecentCacheItem {
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
					
					item.prev = nil
					item.next = mostRecentCacheItem
					
					mostRecentCacheItem?.prev = item
					mostRecentCacheItem = item
				}
				
				#if Cache_Enable_Statistics
				hitCount += 1
				#endif
				return item.value
				
			} else {
				
				#if Cache_Enable_Statistics
				missCount += 1
				#endif
				return nil
			}
		}
		
		set(newValue) {
		
			guard let newValue = newValue else {
				return removeValue(forKey: key)
			}
			
			if let existingItem = _dict[key] {
				
				// Update item value
				existingItem.value = newValue
				
				if existingItem !== mostRecentCacheItem {
					
					// Remove item from current position in linked-list
					//
					// Notes:
					// We fetched the item from the list,
					// so we know there's a valid mostRecentCacheItem & leastRecentCacheItem.
					// Furthermore, we know the item isn't the mostRecentCacheItem.
					
					existingItem.prev?.next = existingItem.next
					
					if existingItem === leastRecentCacheItem {
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
					
					existingItem.prev = nil
					existingItem.next = mostRecentCacheItem
					
					mostRecentCacheItem?.prev = existingItem
					mostRecentCacheItem = existingItem
				}
				
			} else {
				
				let newItem = CacheItem(key: key, value: newValue)
				_dict[key] = newItem
				
				// Add item to beginning of linked-list
				
				newItem.next = mostRecentCacheItem
				
				mostRecentCacheItem?.prev = newItem
				mostRecentCacheItem = newItem
				
				// Evict leastRecentCacheItem if needed
				
				if (countLimit > 0) && (_dict.count > countLimit) {
					
					let keyToEvict = leastRecentCacheItem!.key
					
					leastRecentCacheItem = leastRecentCacheItem?.prev
					leastRecentCacheItem?.next = nil
					
					_dict[keyToEvict] = nil
					
					#if Cache_Enable_Statistics
					evictionCount += 1
					#endif
				}
				else
				{
					if leastRecentCacheItem == nil {
						// There is only 1 item in list.
						// leastRecentCacheItem === mostRecentCacheItem === newItem
						leastRecentCacheItem = newItem
					}
				}
			}
		}
	}
	
	func removeValue(forKey key: Key) -> Void {
		
		if let item = _dict[key] {
			
			if mostRecentCacheItem === item {
				mostRecentCacheItem = item.next
			} else {
				item.prev?.next = item.next
			}
				
			if leastRecentCacheItem === item {
				leastRecentCacheItem = item.prev
			} else {
				item.next?.prev = item.prev
			}
				
			_dict[key] = nil
		}
	}
	
	func contains(key: Key) -> Bool {
		
		return _dict[key] != nil
	}
	
	func filteredKeys(_ isIncluded: (Key) -> Bool) -> [Key] {
		
		var results = [Key]()
		
		var item = mostRecentCacheItem
		while item != nil {
			
			if isIncluded(item!.key) {
				results.append(item!.key)
			}
			
			item = item?.next
		}
		
		return results
	}
}
