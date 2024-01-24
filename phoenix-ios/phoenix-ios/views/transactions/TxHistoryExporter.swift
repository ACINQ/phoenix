import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TxHistoryExporter"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct TxHistoryExporter: View {
	
	@State var startDate = Date()
	@State var endDate = Date()
	
	@State var includeDescription = true
	@State var includeNotes = true
	@State var includeFiat = true
	@State var includeOriginDestination = true
	
	@State var invalidDates = false
	@State var isExporting = false
	@State var shareUrl: URL? = nil
	@State var paymentCount: Int? = nil
	@State var exportedCount: Int? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	
	let FETCH_ROWS_BATCH_COUNT = 32
	
	let FIELD_ID          = "ID"
	let FIELD_DATE        = "Date"
	let FIELD_AMOUNT_BTC  = "Amount BTC"
	let FIELD_AMOUNT_FIAT = "Amount Fiat"
	let FIELD_DESCRIPTION = "Description"
	let FIELD_NOTES       = "Notes"
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Export Payments", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			Color.primaryBackground
				.ignoresSafeArea(.all, edges: .all)
			
			wrappedContent()
			
			toast.view()
		}
		.onAppear {
			onAppear()
		}
		.onChange(of: startDate) { _ in
			datesChanged()
		}
		.onChange(of: endDate) { _ in
			datesChanged()
		}
	}
	
	@ViewBuilder
	func wrappedContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Color.primaryBackground.frame(height: 25)
			ScrollView(.vertical) {
				content()
			}
		}
		.sheet(isPresented: shareUrlBinding()) {
			let items: [Any] = [shareUrl!]
			ActivityView(activityItems: items, applicationActivities: nil)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Export your payment history in CSV (comma separated value) format.")
				.padding(.bottom, 40)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Start date")
					.padding(.trailing, 4)
				Text("(inclusive)")
					.foregroundColor(.secondary)
				Spacer()
				DatePicker("", selection: $startDate, displayedComponents: .date)
					 .labelsHidden()
			} // </HStack>
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("End date")
					.padding(.trailing, 4)
				Text("(inclusive)")
					.foregroundColor(.secondary)
				Spacer()
				DatePicker("", selection: $endDate, displayedComponents: .date)
					.labelsHidden()
			} // </HStack>
			.padding(.bottom, 40)
			
			Toggle(isOn: $includeDescription) {
				Text("Include description")
			}
			.disabled(isExporting)
			.padding(.trailing, 2)
			.padding(.bottom)
			
			Toggle(isOn: $includeNotes) {
				Text("Include notes")
			}
			.disabled(isExporting)
			.padding(.trailing, 2)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.centerTopLine) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text("Include original fiat value")
						.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
							d[VerticalAlignment.center]
						}
					Text("(at time of payment)")
						.foregroundColor(.secondary)
						.padding(.leading, 4)
				}
				Spacer()
				Toggle("", isOn: $includeFiat)
					.labelsHidden()
					.disabled(isExporting)
					.padding(.trailing, 2)
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center]
					}
					
			} // </HStack>
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.centerTopLine) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text("Include origin & destination")
						.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
							d[VerticalAlignment.center]
						}
					Text("(e.g. btc address payment was sent to)")
						.foregroundColor(.secondary)
						.padding(.leading, 4)
				}
				Spacer()
				Toggle("", isOn: $includeOriginDestination)
					.labelsHidden()
					.disabled(isExporting)
					.padding(.trailing, 2)
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center]
					}
					
			} // </HStack>
			.padding(.bottom, 40)
			
			HStack {
				Spacer()
				VStack(alignment: HorizontalAlignment.center) {
					exportButton()
					exportFooter()
				}
				Spacer()
			}
			
		} // </VStack>
		.padding()
	}
	
	@ViewBuilder
	func exportButton() -> some View {

		Button {
			performExport()
		} label: {
			Label("Export", systemImage: "square.and.arrow.up")
				.font(.title3)
		}
		.buttonStyle(.borderedProminent)
		.buttonBorderShape(.capsule)
		.disabled(invalidDates || isExporting)
	}
	
	@ViewBuilder
	func exportFooter() -> some View {
		
		if invalidDates {
			
			Text("Invalid date range")
				.foregroundColor(.appNegative)
				.padding(.top, 8)
			
		} else if isExporting {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 10) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				if let exportedCount {
					if let paymentCount, exportedCount <= paymentCount {
						Text("\(exportedCount) of \(paymentCount)").font(.subheadline)
					} else {
						Text("\(exportedCount)").font(.subheadline)
					}
				}
			}
			
		} else if let paymentCount {
			
			if paymentCount == 1 {
				Text("1 payment")
					.padding(.top, 8)
			} else {
				Text("\(paymentCount) payments")
					.padding(.top, 8)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		fetchOldestPaymentDate()
	}
	
	func datesChanged() {
		log.trace("datesChanged()")
		
		// Remember: startDate and endDate can be the same day.
		
		let startMillis = sanitizeStartDate()
		let endMillis = sanitizeEndDate()
		
		if startMillis > endMillis {
			invalidDates = true
			paymentCount = nil
		} else {
			invalidDates = false
			paymentCount = nil
			refreshPaymentCount()
		}
	}
	
	func performExport() {
		log.trace("performExport()")
		
		isExporting = true
		exportedCount = nil
		Task {
			await asyncExport()
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	private func shareUrlBinding() -> Binding<Bool> {
		
		return Binding(
			get: {
				return shareUrl != nil
			},
			set: {
				if !$0 {
					if let tmpFileUrl = shareUrl {
						shareUrl = nil
						DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 5.0) {
							do {
								try FileManager.default.removeItem(at: tmpFileUrl)
								log.debug("Deleted tmp file: \(tmpFileUrl)")
							} catch {
								log.error("Error deleting tmp file: \(error)")
							}
						}
					}
				}
			}
		)
	}
	
	private func fetchOldestPaymentDate() {
		log.trace("fetchOldestPaymentDate()")
		
		let databaseManager = Biz.business.databaseManager
		databaseManager.paymentsDb { (result: SqlitePaymentsDb?, _) in
			
			assertMainThread()
			guard let paymentsDb = result else {
				return
			}
			
			paymentsDb.getOldestCompletedDate { (millis: KotlinLong?, _) in
				
				if let oldestDate = self.millisToDate(millis) {
					log.debug("oldestDate = \(oldestDate)")
					startDate = oldestDate
				}
			}
		}
	}
	
	private func sanitizeStartDate() -> Int64 {
		
		let startOfDay = Calendar.current.startOfDay(for: startDate)
		return Int64(startOfDay.timeIntervalSince1970 * 1_000)
	}
	
	private func sanitizeEndDate() -> Int64 {
		
		var components = DateComponents()
		components.day = 1
		components.second = -1
		
		let endOfDay = Calendar.current.date(byAdding: components, to: endDate)!
		return Int64(endOfDay.timeIntervalSince1970 * 1_000)
	}
	
	
	private func refreshPaymentCount() {
		log.trace("refreshPaymentCount()")
		
		let startMillis = sanitizeStartDate()
		let endMillis = sanitizeEndDate()
		
		let databaseManager = Biz.business.databaseManager
		databaseManager.paymentsDb { (result: SqlitePaymentsDb?, _) in
			
			assertMainThread()
			guard let paymentsDb = result else {
				return
			}
			
			paymentsDb.listRangeSuccessfulPaymentsCount(
				startDate: startMillis,
				endDate: endMillis
			) { (result: KotlinLong?, _) in
				
				let count = result?.intValue ?? 0
				if (startMillis == self.sanitizeStartDate()) && (endMillis == self.sanitizeEndDate()) {
					paymentCount = count
				} else {
					// result no longer matches UI; user changed dates
				}
			}
		}
	}
	
	private func millisToDate(_ millis: KotlinLong?) -> Date? {
			
		if let millis = millis {
			let seconds: TimeInterval = millis.doubleValue / Double(1_000)
			return Date(timeIntervalSince1970: seconds)
		} else {
			return nil
		}
	}
	
	@MainActor
	private func exportingFailed() {
		log.trace("exportingFailed()")
		
		isExporting = false
		toast.pop(
			NSLocalizedString("Exporting Failed", comment: "TxHistoryExporter"),
			colorScheme: colorScheme.opposite,
			style: .chrome,
			duration: 15.0,
			alignment: .middle,
			showCloseButton: true
		)
	}
	
	@MainActor
	private func exportingFinished(_ tempFile: URL) {
		log.trace("exportingFinished()")
		
		isExporting = false
		shareUrl = tempFile
	}
	
	// --------------------------------------------------
	// MARK: Exporting
	// --------------------------------------------------
	
	@MainActor
	private func asyncExport() async {
		log.trace("asyncExport()")
		
		let startMillis = sanitizeStartDate()
		let endMillis = sanitizeEndDate()
		
		let tmpDir = FileManager.default.temporaryDirectory
		let tmpFilename = "phoenix.csv"
		let tmpFile = tmpDir.appendingPathComponent(tmpFilename, isDirectory: false)
		
		if !FileManager.default.fileExists(atPath: tmpFile.path) {
			FileManager.default.createFile(atPath: tmpFile.path, contents: nil)
		}
		
		guard let fileHandle = try? FileHandle(forWritingTo: tmpFile) else {
			return exportingFailed()
		}
		
		exportedCount = 0
		
		let databaseManager = Biz.business.databaseManager
		let peerManager = Biz.business.peerManager
		let fetcher = Biz.business.paymentsManager.fetcher
		
		do {
			let paymentsDb = try await databaseManager.paymentsDb()
			let peer = try await peerManager.getPeer()
			
			let config = CsvWriter.Configuration(
				includesFiat: includeFiat,
				includesDescription: includeDescription,
				includesNotes: includeNotes,
				includesOriginDestination: includeOriginDestination,
				swapInAddress: peer.swapInAddress
			)
			
			var done = false
			var rowsOffset = 0
			
			let headerRowStr = CsvWriter.companion.makeHeaderRow(config: config)
			let headerRowData = Data(headerRowStr.utf8)
			
			try await fileHandle.asyncWrite(data: headerRowData)
			
			while !done {
				
				let rows: [WalletPaymentOrderRow] = try await paymentsDb.listRangeSuccessfulPaymentsOrder(
					startDate: startMillis,
					endDate: endMillis,
					count: Int32(FETCH_ROWS_BATCH_COUNT),
					skip: Int32(rowsOffset)
				)
				
				for row in rows {
					
					guard let info = try await fetcher.getPayment(
						row: row,
						options: WalletPaymentFetchOptions.companion.All
					) else {
						continue
					}
					
					let localizedDescription = info.paymentDescription() ?? info.defaultPaymentDescription()
					let rowStr = CsvWriter.companion.makeRow(
						info: info,
						localizedDescription: localizedDescription,
						config: config
					)
					let rowData = Data(rowStr.utf8)
					
					try await fileHandle.asyncWrite(data: rowData)
					exportedCount = (exportedCount ?? 0) + 1
					
				} // </for row in rows>
		
				rowsOffset += rows.count
				
				if rows.count < FETCH_ROWS_BATCH_COUNT {
					done = true
				} else {
					// there may be more; fetch another batch
				}
				
			} // </while !done>
			
			try await fileHandle.asyncSyncAndClose()
			exportingFinished(tmpFile)
			
		} catch {
			log.error("Error: \(error)")
			exportingFailed()
		}
	}
}
