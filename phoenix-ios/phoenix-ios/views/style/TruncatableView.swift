/// Credit:
/// https://www.fivestars.blog/articles/trucated-text/
/// https://www.fivestars.blog/articles/swiftui-share-layout-information/

import SwiftUI

fileprivate let filename = "TruncatableView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct TruncatableView<Content: View>: View {
	
	let identifier: String
	let fixedHorizontal: Bool
	let fixedVertical: Bool
	let content: Content
	let wasTruncated: () -> Void
	
	@State private var renderedSize: [DynamicTypeSize: CGSize] = [:]
	@State private var intrinsicSize: [DynamicTypeSize: CGSize] = [:]
	@State private var triggered: Bool = false
	
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
	init(
		fixedHorizontal: Bool,
		fixedVertical: Bool,
		@ViewBuilder builder: () -> Content,
		wasTruncated: @escaping () -> Void
	) {
		self.identifier = ""
		self.fixedHorizontal = fixedHorizontal
		self.fixedVertical = fixedVertical
		self.content = builder()
		self.wasTruncated = wasTruncated
	}
	
	init(
		identifier: String,
		fixedHorizontal: Bool,
		fixedVertical: Bool,
		@ViewBuilder builder: () -> Content,
		wasTruncated: @escaping () -> Void
	) {
		self.identifier = identifier
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
				log.trace("[\(identifier)] readSize(foreground) = \(size.width)")
				renderedSize[dts] = size
				DispatchQueue.main.async { // Read note below
					checkForTruncation(dts)
				}
			}
			.background(
				content
					.fixedSize(horizontal: fixedHorizontal, vertical: fixedVertical)
					.hidden()
					.readSize { size in
						log.trace("[\(identifier)] readSize(background) = \(size.width)")
						renderedSize[dts] = nil
						intrinsicSize[dts] = size
					}
			)
	}
	
	// We're seeing seeing the following issues: (may only affect iOS 16)
	//
	// 1. the background is rendered at size X
	// 2. the foreground is rendered at size X
	// 3. the background is re-rendered at size Y <-- this event here was a problem
	// 4. the foreground is re-rendered at size Y
	//
	// ^^ at step 3, if (Y.width < X.width) or (Y.height < X.height)
	// then it would incorrectly trigger the truncation notification.
	//
	// We also saw this problem:
	// 1. the background is rendered at size X
	// 2. the foreground is rendered at size Y <-- this event here was a problem
	// 3. the foreground is re-rendered at size X
	//
	// ^^ at step 2, if if (Y.width < X.width) or (Y.height < X.height)
	// then it would incorrectly trigger the truncation notification.
	//
	// One thing we also noticed is that the foreground is ALWAYS rendered (and measured) AFTER the background.
	//
	// So we can take advantage of this and:
	// - only call `checkForTrunaction` after the foreground is rendered
	// - invalidate the foreground measurement if the background is re-rendered
	// - invoke `checkForTruncation` on the next runloop cycle to allow for re-renders
	//
	
	func checkForTruncation(_ dts: DynamicTypeSize) {
		guard !triggered, let rSize = renderedSize[dts], let iSize = intrinsicSize[dts] else {
			return
		}
		let truncatedWidth = rSize.width < iSize.width
		let truncatedHeight = rSize.height < iSize.height
		
		if truncatedWidth || truncatedHeight {
			if truncatedWidth && truncatedHeight {
				log.debug(
					"""
					[\(identifier)]
					rSize.width(\(rSize.width)) < iSize.width(\(iSize.width)) || \
					rSize.height(\(rSize.height)) < iSize.height(\(iSize.height))
					"""
				)
			} else if truncatedWidth {
				log.debug("[\(identifier)] rSize.width(\(rSize.width)) < iSize.width(\(iSize.width))")
			} else {
				log.debug("[\(identifier)] rSize.height(\(rSize.height)) < iSize.height(\(iSize.height))")
			}
			triggered = true
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
