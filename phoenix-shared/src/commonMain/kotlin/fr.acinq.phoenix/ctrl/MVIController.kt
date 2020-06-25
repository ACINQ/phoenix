package fr.acinq.phoenix.ctrl

abstract class MVIController<M, I> {

    abstract fun subscribe(onModel: (M) -> Unit): () -> Unit

    abstract fun process(intent: I)


    class Mock<M, I>(val model: M) : MVIController<M, I>() {
        override fun subscribe(onModel: (M) -> Unit): () -> Unit {
            onModel(model)
            return ({})
        }
        override fun process(intent: I) {}
    }

}
