package fr.acinq.phoenix.ctrl


typealias ScanController = MVI.Controller<Scan.Model, Scan.Intent>

object Scan {

    sealed class Model : MVI.Model() {
        object Ready: Model()

        data class Validate(val request: String, val amountMsat: Long?, val requestDescription: String?): Model()
        data class Sending(val amountMsat: Long, val requestDescription: String?): Model()
//        data class Fulfilled(val amountMsat: Long, val requestDescription: String?): Model()
    }

    sealed class Intent : MVI.Intent() {
        data class Parse(val request: String) : Intent()
        data class Send(val request: String, val amountMsat: Long) : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}
