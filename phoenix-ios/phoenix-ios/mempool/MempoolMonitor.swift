import Foundation
import Combine
import Network
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MempoolMonitor"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


actor MempoolMonitor {
	
	static let shared = MempoolMonitor()
	
	private var listenerCount: UInt32 = 0
	private var refreshTask: Task<Void, Never>? = nil
	
	/// Must use public `stream()` function defined below
	private let responsePublisher = CurrentValueSubject<MempoolRecommendedResponse?, Never>(nil)
	
	/// Fires when the internet status switches from disconnected to connected.
	private let internetReconnectedPublisher = PassthroughSubject<Void, Never>()
	
	private let networkMonitor = NWPathMonitor()
	
	/// Must use shared instance
	private init() {
		log.trace("init()")
		
		Task {
			await startNetworkMonitor()
		}
	}
	
	private func startNetworkMonitor() {
		log.trace("startNetworkMonitor()")
		
		var wasDisconnected = true
		networkMonitor.pathUpdateHandler = {(path: NWPath) -> Void in
			
			let hasInternet: Bool
			switch path.status {
				case .satisfied          : hasInternet = true
				case .unsatisfied        : hasInternet = false
				case .requiresConnection : hasInternet = true
				@unknown default         : hasInternet = false
			}
			
			if hasInternet && wasDisconnected {
				log.debug("Detected internet reconnection...")
				self.internetReconnectedPublisher.send()
			}
			wasDisconnected = !hasInternet
		}
		
		networkMonitor.start(queue: DispatchQueue.main)
	}
	
	private func incrementListenerCount() {
		log.trace("incrementListenerCount()")
		
		if listenerCount <= (UInt32.max - 1) {
			listenerCount += 1
		}
		
		if listenerCount == 1 {
			startRefreshTask()
		}
	}
	
	private func decrementListenerCount() {
		log.trace("decrementListenerCount()")
		
		if listenerCount > 0 {
			listenerCount -= 1
		}
		
		if listenerCount == 0 {
			stopRefreshTask()
		}
	}
	
	private func startRefreshTask() {
		log.trace("startRefreshTask()")
		
		guard refreshTask == nil else {
			log.debug("startRefreshTask: already started")
			return
		}
		
		refreshTask = Task {
			
			// If there are active listeners, then we refresh every 5 minutes.
			// If there are no active listeners, then this task is cancelled, and we don't refresh at all.
			let successRefreshDelay = 5.minutes()
			
			// If we have a recent response from the server, then we don't need to immediately refresh.
			if let lastResponse = self.responsePublisher.value {
				let elapsed = lastResponse.timestamp.timeIntervalSinceNow * -1.0
				if elapsed < successRefreshDelay {
					let delay = successRefreshDelay - elapsed
					do {
						log.debug("Last response is still fresh...")
						try await Task.sleep(seconds: delay)
					} catch {}
					if Task.isCancelled {
						return
					}
				}
			}
			
			repeat {
				
				let result = await MempoolRecommendedResponse.fetch()
				
				switch result {
				case .success(let response):
					log.debug("Successfully refreshed mempool.space/recommended")
					self.responsePublisher.send(response)
					
					do {
						try await Task.sleep(seconds: successRefreshDelay)
					} catch {}
					
				case .failure(let reason):
					log.error("Errror fetching mempool.space/recommended: \(reason)")
					
					let delay = 15.seconds()
					let sleepTask = Task {
						try await Task.sleep(seconds: delay)
					}
					
					// We might have failed due to an internet connectivity issue.
					// So if the internet connection is restored, we should immediately retry.
					let waitForInternetTask = Task {
						for try await _ in self.internetReconnectedPublisher.values {
							return sleepTask.cancel()
						}
					}
					
					do {
						try await sleepTask.value
						waitForInternetTask.cancel()
					} catch {}
				}
				
			} while !Task.isCancelled
		}
	}
	
	private func stopRefreshTask() {
		log.trace("stopRefreshTask()")
		
		guard refreshTask != nil else {
			log.debug("stopRefreshTask: already stopped")
			return
		}
		
		refreshTask?.cancel()
		refreshTask = nil
	}
	
	private func nextResponse(
		_ previousResponse: MempoolRecommendedResponse?
	) async -> MempoolRecommendedResponse? {
		
		for try await response in self.responsePublisher.values {
			if let response {
				if let previousResponse {
					// This is a subsequent request from the AsyncStream (e.g. second request).
					// So we can only send the response if it's differnt from the previous response.
					
					if response != previousResponse {
						
						log.debug("Issuing fresh response...")
						return response
					}
					
				} else {
					// This is the first time the AsyncStream has requested a value.
					// So the response is the last cached value in the publisher.
					// As long as it's not too old, we can relay it to the view.
					// This allows the view to update itself with semi-accurate information.
					// And the view will automatically refresh after we've refreshed the response.
					
					let elapsed = response.timestamp.timeIntervalSinceNow * -1.0
					if elapsed <= 1.hours() {
						
						log.debug("Re-issuing non-stale cached response...")
						return response
					}
				}
			}
		}
		
		// The publisher above never finishes, so the code below is unreachable.
		// But the compiler doesn't know that.
		return nil
	}
	
	nonisolated func stream() -> AsyncStream<MempoolRecommendedResponse> {
		log.trace("stream()")
		
		Task {
			await self.incrementListenerCount()
		}
		
		var lastElement: MempoolRecommendedResponse? = nil
		return AsyncStream(unfolding: {
			
			let element = await self.nextResponse(lastElement)
			lastElement = element
			return element
			
		}, onCancel: {
			
			Task {
				await self.decrementListenerCount()
			}
		})
	}
}
