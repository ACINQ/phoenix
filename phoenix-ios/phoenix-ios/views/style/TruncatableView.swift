/// Credit:
/// https://www.fivestars.blog/articles/trucated-text/
/// https://www.fivestars.blog/articles/swiftui-share-layout-information/

import SwiftUI

struct TruncatableView<Content: View>: View {
	
	let fixedHorizontal: Bool
	let fixedVertical: Bool
	let content: Content
	let wasTruncated: () -> Void
	
	@State private var renderedSize: [DynamicTypeSize: CGSize] = [:]
	@State private var intrinsicSize: [DynamicTypeSize: CGSize] = [:]
	
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
	init(
		fixedHorizontal: Bool,
		fixedVertical: Bool,
		@ViewBuilder builder: () -> Content,
		wasTruncated: @escaping () -> Void
	) {
		self.fixedHorizontal = fixedHorizontal
		self.fixedVertical = fixedVertical
		self.content = builder()
		self.wasTruncated = wasTruncated
	}
	
	@ViewBuilder
	var body: some View {
		let dts = self.dynamicTypeSize
		content
			.readSize { size in
				renderedSize[dts] = size
				checkForTruncation(dts)
			}
			.background(
				content
					.fixedSize(horizontal: fixedHorizontal, vertical: fixedVertical)
					.hidden()
					.readSize { size in
						intrinsicSize[dts] = size
						checkForTruncation(dts)
					}
			)
	}
	
	func checkForTruncation(_ dts: DynamicTypeSize) {
		guard let rSize = renderedSize[dts], let iSize = intrinsicSize[dts] else {
			return
		}
		if rSize.width < iSize.width || rSize.height < iSize.height {
			wasTruncated()
		}
	}
}

public extension View {
	
	func readSize(onChange: @escaping (CGSize) -> Void) -> some View {
		background(
			GeometryReader { geometryProxy in
				Color.clear
					.preference(key: SizePreferenceKey.self, value: geometryProxy.size)
			}
		)
		.onPreferenceChange(SizePreferenceKey.self, perform: onChange)
	}
}

private struct SizePreferenceKey: PreferenceKey {
	static var defaultValue: CGSize = .zero
	static func reduce(value: inout CGSize, nextValue: () -> CGSize) {}
}

extension DynamicTypeSize: CustomStringConvertible {
	public var description: String {
		switch self {
			case .xSmall         : return "xSmall"
			case .small          : return "small"
			case .medium         : return "medium"
			case .large          : return "large"
			case .xLarge         : return "xLarge"
			case .xxLarge        : return "xxLarge"
			case .xxxLarge       : return "xxxLarge"
			case .accessibility1 : return "accessibility1"
			case .accessibility2 : return "accessibility2"
			case .accessibility3 : return "accessibility3"
			case .accessibility4 : return "accessibility4"
			case .accessibility5 : return "accessibility5"
			@unknown default     : return "unknown"
		}
	}
}
