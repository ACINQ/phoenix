package fr.acinq.eclair.phoenix

import androidx.test.runner.AndroidJUnit4
import fr.acinq.eclair.payment.PaymentRequest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentRequestTest {
  @Test
  fun readTest() {
    val raw ="lnbc1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5kgetjypeh2ursdae8g6twvus8g6rfwvs8qun0dfjkxaq8rkx3yf5tcsyz3d73gafnh3cax9rn449d9p5uxz9ezhhypd0elx87sjle52x86fux2ypatgddc6k63n7erqz25le42c4u4ecky03ylcqca784w"
    val start = System.currentTimeMillis()
    var dummy = 0
    for(i in 1..50) {
      val pr = PaymentRequest.read(raw, true)
      val nodeId = pr.nodeId()
      dummy += nodeId.value()[0]
    }
    val end = System.currentTimeMillis()
    println("total duration = ${end - start} ms")
  }
}
