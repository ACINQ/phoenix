import SwiftUI
import PhoenixShared
import CodableCSV
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
		
		if #available(iOS 15, *) {

			Button {
				performExport()
			} label: {
				Label("Export", systemImage: "square.and.arrow.up")
					.font(.title3)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.disabled(invalidDates || isExporting)

		} else {
			
			Button {
				performExport()
			} label: {
				Label("Export", systemImage: "square.and.arrow.up")
					.font(.title3)
					.foregroundColor(Color.white)
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
			}
			.buttonStyle(
				ScaleButtonStyle(
					cornerRadius: 100,
					backgroundFill: Color.appAccent,
					disabledBackgroundFill: Color.appAccent.opacity(0.65),
					pressedOpacity: 0.4,
					disabledOpacity: 0.4
				)
			)
			.disabled(invalidDates || isExporting)
		}
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
		
		if startDate > endDate {
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
						DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 5.0) {
							do {
								try FileManager.default.removeItem(at: tmpFileUrl)
								log.debug("Deleted tmp file: \(tmpFileUrl)")
							} catch {
								log.error("Error deleting tmp file: \(error)")
							}
						}
						shareUrl = nil
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
			
			paymentsDb.listRangePaymentsCount(
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
		
		var headers = [
			FIELD_ID,
			FIELD_DATE,
			FIELD_AMOUNT_BTC
		]
		if includeFiat {
			headers.append(FIELD_AMOUNT_FIAT)
		}
		if includeDescription {
			headers.append(FIELD_DESCRIPTION)
		}
		if includeNotes {
			headers.append(FIELD_NOTES)
		}
		
		var writerConfig = CSVWriter.Configuration()
		writerConfig.encoding = .utf8
		writerConfig.delimiters = (field: ",", row: "\n")
		writerConfig.headers = headers
		
		let writer: CSVWriter
		do {
			writer = try CSVWriter(fileURL: tmpFile, append: false)
			try writer.write(row: headers)
		} catch {
			log.error("Unable to create CSVWriter: \(error)")
			return exportingFailed()
		}
		
		exportedCount = 0
		
		let databaseManager = Biz.business.databaseManager
		let fetcher = Biz.business.paymentsManager.fetcher
		
		do {
			let paymentsDb = try await databaseManager.paymentsDb()
			
			var done = false
			var rowsOffset = 0
			
			while !done {
				
				let rows: [WalletPaymentOrderRow] = try await paymentsDb.listRangePaymentsOrder(
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
					
					let id = info.payment.id()
					let date = iso8601String(info)
					
					let amtMsat: Int64
					if info.payment.isOutgoing() {
						amtMsat = -info.payment.amount.msat
					} else {
						amtMsat = info.payment.amount.msat
					}
					
					let amtBtc = "\(amtMsat) msat"
					
					try writer.write(field: id)     // FIELD_ID
					try writer.write(field: date)   // FIELD_DATE
					try writer.write(field: amtBtc) // FIELD_AMOUNT_BTC
					
					if includeFiat {
						// Developer notes:
						//
						// - The fiat amount may not always be in the same currency.
						//   That is, the user has the ability to set their preferred fiat currency in the app.
						//   For example:
						//   * user lives in USA, has currency set to USD
						//     * payments will record USD/BTC exchange rate at time of payment
						//     * exported payments will read "X.Y USD"
						//   * user goes on vacation in Mexico, changes currency to MXN
						//     * payments will record MXN/BTC exchange rate at time of payment
						//     * exported payments will read "X.Y MXN"
						//   * user moves to Spain, changes currency to EUR
						//     * payments will record EUR/BTC exchange rate at time of payment
						//     * exported payments will read "X.Y EUR"
						//
						// - Prior to v1.5.5, the exchange rates for fiat currencies other
						//   than USD & EUR may have been unreliable. So if you're parsing,
						//   for example COP (Colombian Pesos), and you have an alternative
						//   source for fetching historical exchange rates, then you may
						//   prefer that source over the CSV values.
						//   v1.5.5 was released around Feb 1, 2023
						
						if let originalExchangeRate = info.metadata.originalFiat {
							let amtFiat = Utils.formatFiat(msat: amtMsat, exchangeRate: originalExchangeRate)
							try writer.write(field: amtFiat.string)
						} else {
							try writer.write(field: "") // unknown
						}
					}
					
					if includeDescription {
						let description = info.paymentDescription() ?? info.defaultPaymentDescription()
						try writer.write(field: description)
					}
					if includeNotes {
						let notes = info.metadata.userNotes ?? ""
						try writer.write(field: notes)
					}
					
					try writer.endRow()
					exportedCount = (exportedCount ?? 0) + 1
				}
				
				rowsOffset += rows.count
				
				if rows.count < FETCH_ROWS_BATCH_COUNT {
					done = true
				} else {
					// there may be more; fetch another batch
				}
				
			} // </while !done>
			
			try writer.endEncoding()
			exportingFinished(tmpFile)
			
		} catch {
			log.error("Error: \(error)")
			exportingFailed()
		}
	}
	
	private func iso8601String(_ info: WalletPaymentInfo) -> String {
		
		let date = info.payment.completedAtDate() ?? info.payment.createdAtDate()
		if #available(iOS 15, *) {
			return date.ISO8601Format()
			
		} else {
			let formatter = ISO8601DateFormatter()
			return formatter.string(from: date)
		}
	}
}
