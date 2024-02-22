import SwiftUI
import PhoenixShared

fileprivate let filename = "LiquidityPolicyHelp"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LiquidityPolicyHelp: View {
	
	@Binding var isShowing: Bool
	
	var body: some View {
		
		ZStack {
		
			LocalWebView(
				html: LiquidityHTML(),
				scrollIndicatorInsets: UIEdgeInsets(top: 0, left: 0, bottom: 0, right: -20)
			)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.padding(.top, 40)
			.padding(.leading, 20)
			.padding(.trailing, 20) // must match LocalWebView.scrollIndicatorInsets.right
			
			// close button
			// (required for landscapse mode, where swipe-to-dismiss isn't possible)
			VStack {
				HStack {
					Spacer()
					Button {
						close()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
				}
				Spacer()
			}
			.padding()
		}
	}
	
	func close() {
		log.trace("close()")
		
		isShowing = false
	}
}
