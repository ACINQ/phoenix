package fr.acinq.phoenix.ctrl


object MVI {

    abstract class Data {
        override fun toString() = this::class.simpleName ?: super.toString()
    }

    abstract class Model : Data() {
        override fun toString() = super.toString().lines().joinToString(" ") { it.trim() }.take(100)
    }
    abstract class Intent : Data()

    abstract class Controller<M : Model, I : Intent>(val firstModel: M) {

        abstract fun subscribe(onModel: (M) -> Unit): () -> Unit

        abstract fun intent(intent: I)

        abstract fun stop()

        open class Mock<M : Model, I : Intent>(val model: M) : Controller<M, I>(model) {
            override fun subscribe(onModel: (M) -> Unit): () -> Unit {
                onModel(model)
                return ({})
            }
            override fun intent(intent: I) {}
            override fun stop() {}
        }

    }

}
