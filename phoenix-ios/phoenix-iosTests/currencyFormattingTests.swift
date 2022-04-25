import XCTest
@testable import Phoenix

class currencyFormattingTests: XCTestCase {

	let en_US = Locale(identifier: "en_US")
	
	override func setUpWithError() throws {
		// Put setup code here.
		// This method is called before the invocation of each test method in the class.
	}

	override func tearDownWithError() throws {
		// Put teardown code here.
		// This method is called after the invocation of each test method in the class.
	}
	
	func f(_ amount: String) -> String {
		return amount.replacingOccurrences(of: "_", with: FormattedAmount.fractionGroupingSeparator)
	}
	
	func testSats() {

		// Tests where: bitcoinUnit == .sat
		
		do { // standard formatting
			
			let t1 = Utils.formatBitcoin(sat: 100, bitcoinUnit: .sat, locale: en_US)
			XCTAssert(t1.digits == f("100"))
			
			let t2 = Utils.formatBitcoin(sat: 1_000, bitcoinUnit: .sat, locale: en_US)
			XCTAssert(t2.digits == f("1,000"))
			
			let t3 = Utils.formatBitcoin(sat: 1_000_000, bitcoinUnit: .sat, locale: en_US)
			XCTAssert(t3.digits == f("1,000,000"))
		}
		do { // millisatoshi formatting
			
			let t1 = Utils.formatBitcoin(msat: 123, bitcoinUnit: .sat, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t1.digits == f("0.123"))
			
			let t2 = Utils.formatBitcoin(msat: 100, bitcoinUnit: .sat, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t2.digits == f("0.100")) // always 3 digits if millisatoshis != 0
			
			let t3 = Utils.formatBitcoin(msat: 1_000, bitcoinUnit: .sat, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t3.digits == f("1")) // msats is zero => truncate
		}
	}
	
	func testBits() {

		// Tests where: bitcoinUnit == .bit
		
		do { // standard formatting
			
			let t0 = Utils.formatBitcoin(sat: 0, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t0.digits == f("0.00"))
			
			let t1 = Utils.formatBitcoin(sat: 1, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t1.digits == f("0.01"))
			
			let t2 = Utils.formatBitcoin(sat: 10, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t2.digits == f("0.10"))
			
			let t3 = Utils.formatBitcoin(sat: 100, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t3.digits == f("1.00"))
			
			let t4 = Utils.formatBitcoin(sat: 1_000, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t4.digits == f("10.00"))
			
			let t5 = Utils.formatBitcoin(sat: 10_000, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t5.digits == f("100.00"))
			
			let t6 = Utils.formatBitcoin(sat: 100_000, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t6.digits == f("1,000.00"))
			
			let t7 = Utils.formatBitcoin(sat: 1_000_000, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t7.digits == f("10,000.00"))
			
			let t8 = Utils.formatBitcoin(sat: 10_000_000, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t8.digits == f("100,000.00"))
			
			let t9 = Utils.formatBitcoin(sat: 100_000_000, bitcoinUnit: .bit, locale: en_US)
			XCTAssert(t9.digits == f("1,000,000.00"))
		}
		do { // millisatoshi formatting
			
			let t1 = Utils.formatBitcoin(msat: 123, bitcoinUnit: .bit, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t1.digits == f("0.00_123"))
			
			let t2 = Utils.formatBitcoin(msat: 100, bitcoinUnit: .bit, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t2.digits == f("0.00_100")) // always 5 digits if millisatoshis != 0
			
			let t3 = Utils.formatBitcoin(msat: 1_000, bitcoinUnit: .bit, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t3.digits == f("0.01")) // msats is zero => truncate
		}
	}
	
	func testMbtc() {
		
		// Tests where: bitcoinUnit == .mbtc
		
		do { // standard formatting
			
			let t0 = Utils.formatBitcoin(sat: 0, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t0.digits == f("0"))
			
			let t1 = Utils.formatBitcoin(sat: 1, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t1.digits == f("0.00_001"))
			
			let t2 = Utils.formatBitcoin(sat: 10, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t2.digits == f("0.00_010"))
			
			let t3 = Utils.formatBitcoin(sat: 100, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t3.digits == f("0.00_100"))
			
			let t4 = Utils.formatBitcoin(sat: 1_000, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t4.digits == f("0.01"))
			
			let t5 = Utils.formatBitcoin(sat: 10_000, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t5.digits == f("0.10"))
			
			let t6 = Utils.formatBitcoin(sat: 100_000, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t6.digits == f("1"))
			
			let t7 = Utils.formatBitcoin(sat: 1_000_000, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t7.digits == f("10"))
			
			let t8 = Utils.formatBitcoin(sat: 10_000_000, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t8.digits == f("100"))
			
			let t9 = Utils.formatBitcoin(sat: 100_000_000, bitcoinUnit: .mbtc, locale: en_US)
			XCTAssert(t9.digits == f("1,000"))
		}
		do { // millisatoshi formatting
			
			let t1 = Utils.formatBitcoin(msat: 123, bitcoinUnit: .mbtc, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t1.digits == f("0.00_000_123"))
			
			let t2 = Utils.formatBitcoin(msat: 100, bitcoinUnit: .mbtc, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t2.digits == f("0.00_000_100")) // always 8 digits if millisatoshis != 0
			
			let t3 = Utils.formatBitcoin(msat: 1_000, bitcoinUnit: .mbtc, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t3.digits == f("0.00_001")) // msats is zero => truncate
		}
	}
	
	func testBtc() {
		
		// Tests where: bitcoinUnit == .btc
		
		do { // standard formatting

			let t0 = Utils.formatBitcoin(sat: 0, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t0.digits == f("0"))

			let t1 = Utils.formatBitcoin(sat: 1, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t1.digits == f("0.00_000_001"))

			let t2 = Utils.formatBitcoin(sat: 10, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t2.digits == f("0.00_000_010"))

			let t3 = Utils.formatBitcoin(sat: 100, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t3.digits == f("0.00_000_100"))

			let t4 = Utils.formatBitcoin(sat: 1_000, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t4.digits == f("0.00_001"))

			let t5 = Utils.formatBitcoin(sat: 10_000, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t5.digits == f("0.00_010"))

			let t6 = Utils.formatBitcoin(sat: 100_000, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t6.digits == f("0.00_100"))

			let t7 = Utils.formatBitcoin(sat: 1_000_000, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t7.digits == f("0.01"))

			let t8 = Utils.formatBitcoin(sat: 10_000_000, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t8.digits == f("0.10"))

			let t9 = Utils.formatBitcoin(sat: 100_000_000, bitcoinUnit: .btc, locale: en_US)
			XCTAssert(t9.digits == f("1"))
		}
		do { // millisatoshi formatting
			
			let t1 = Utils.formatBitcoin(msat: 123, bitcoinUnit: .btc, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t1.digits == f("0.00_000_000_123"))
			
			let t2 = Utils.formatBitcoin(msat: 100, bitcoinUnit: .btc, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t2.digits == f("0.00_000_000_100")) // always 11 digits if millisatoshis != 0

			let t3 = Utils.formatBitcoin(msat: 1_000, bitcoinUnit: .btc, policy: .showMsatsIfNonZero, locale: en_US)
			XCTAssert(t3.digits == f("0.00_000_001")) // msats is zero => truncate
		}
	}
}
