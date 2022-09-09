/// Credit:
/// https://www.fivestars.blog/articles/trucated-text/
/// https://www.fivestars.blog/articles/swiftui-share-layout-information/

import SwiftUI

struct TruncatableView<Content: View>: View {
	
	let fixedHorizontal: Bool
	let fixedVertical: Bool
	let content: Content
	let wasTruncated: () -> Void
	
	@State private var renderedSize: [ContentSizeCategory: CGSize] = [:]
	@State private var intrinsicSize: [ContentSizeCategory: CGSize] = [:]
	
	@Environment(\.sizeCategory) private var contentSizeCategory: ContentSizeCategory
	
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
		let csc = self.contentSizeCategory
		content
			.readSize { size in
				renderedSize[csc] = size
				checkForTruncation(csc)
			}
			.background(
				content
					.fixedSize(horizontal: fixedHorizontal, vertical: fixedVertical)
					.hidden()
					.readSize { size in
						intrinsicSize[csc] = size
						checkForTruncation(csc)
					}
			)
	}
	
	func checkForTruncation(_ csc: ContentSizeCategory) {
		guard let rSize = renderedSize[csc], let iSize = intrinsicSize[csc] else {
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
