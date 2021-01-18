import Foundation

/// Credit:
/// - https://stackoverflow.com/a/59307884/43522
/// - https://github.com/DragonCherry/VersionCompare

extension String {

	private func compare(toVersion targetVersion: String) -> ComparisonResult {
		let versionDelimiter = "."
		var result: ComparisonResult = .orderedSame
		var versionComponents = components(separatedBy: versionDelimiter)
		var targetComponents = targetVersion.components(separatedBy: versionDelimiter)

		while versionComponents.count < targetComponents.count {
			versionComponents.append("0")
		}

		while targetComponents.count < versionComponents.count {
			targetComponents.append("0")
		}

		for (version, target) in zip(versionComponents, targetComponents) {
			result = version.compare(target, options: .numeric)
			if result != .orderedSame {
				break
			}
		}

		return result
	}

	func isVersion(equalTo targetVersion: String) -> Bool {
		return compare(toVersion: targetVersion) == .orderedSame
	}

	func isVersion(greaterThan targetVersion: String) -> Bool {
		return compare(toVersion: targetVersion) == .orderedDescending
	}

	func isVersion(greaterThanOrEqualTo targetVersion: String) -> Bool {
		return compare(toVersion: targetVersion) != .orderedAscending
	}

	func isVersion(lessThan targetVersion: String) -> Bool {
		return compare(toVersion: targetVersion) == .orderedAscending
	}

	func isVersion(lessThanOrEqualTo targetVersion: String) -> Bool {
		return compare(toVersion: targetVersion) != .orderedDescending
	}
}
