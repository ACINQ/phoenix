import Foundation

extension UserDefaults {
	
	#if DEBUG
	func dump(
		isKnownKey: (String) -> Bool,
		valueDescription: (String, Any) -> String
	) -> String {
		
		let splitKey = {(key: String) -> (String, String)? in
			
			let dash = "-"
			let comps = key.split(separator: dash)
			
			if comps.count == 2 {
				let prefix = String(comps[0])
				let suffix = String(comps[1])
				return (prefix, suffix)
				
			} else if comps.count > 2 {
				let prefix = comps.prefix(upTo: comps.count - 2).joined(separator: dash)
				let suffix = comps.suffix(2).joined(separator: dash)
				return (prefix, suffix)
				
			} else {
				return nil
			}
		}
		
		let UNKNOWN_ID = "unknown"
		
		var groups: [String: [String: Any]] = [:]
		let everything = self.dictionaryRepresentation()
		
		for (key, value) in everything {
			if isKnownKey(key) {
				
				let groupName: String
				let keyName: String
				
				if let tuple = splitKey(key) {
					groupName = tuple.1 // suffix
					keyName = tuple.0   // prefix
				} else {
					groupName = UNKNOWN_ID
					keyName = key
				}
				
				var groupDict = groups[groupName] ?? [:]
				groupDict[keyName] = value
				groups[groupName] = groupDict
			}
		}
		
		let sortedGroupNames = groups.keys.sorted { (a, b) in
			// return true if `a` should be ordered before `b`; otherwise return false
			if a == UNKNOWN_ID       { return true  }
			if b == UNKNOWN_ID       { return false }
			if a == PREFS_DEFAULT_ID { return true  }
			if b == PREFS_DEFAULT_ID { return false }
			if a == PREFS_GLOBAL_ID  { return true  }
			if b == PREFS_GLOBAL_ID  { return false }
			return a.compare(b) == .orderedAscending
		}
		
		var output: String = ""
		
		for groupName in sortedGroupNames {
			
			output += "# \(groupName):\n"
			
			let groupDict = groups[groupName] ?? [:]
			let sortedKeys = groupDict.keys.sorted()
			
			for key in sortedKeys {
				let value = groupDict[key]!
				let desc = valueDescription(key, value)
				
				output += " - \(key): \(desc)\n"
			}
		}
		
		return output
	}
	#endif
}

#if DEBUG
func printString(_ value: Any) -> String {
	let desc = (value as? String) ?? "unknown"
	return "<String: \(desc)>"
}

func printBool(_ value: Any) -> String {
	let desc = (value as? NSNumber)?.boolValue.description ?? "unknown"
	return "<Bool: \(desc)>"
}

func printInt(_ value: Any) -> String {
	let desc = (value as? NSNumber)?.intValue.description ?? "unknown"
	return "<Int: \(desc)>"
}
#endif

