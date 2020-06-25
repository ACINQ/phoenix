package fr.acinq.phoenix.ctrl


abstract class LogController : MVIController<LogController.Model, Unit>() {

    data class Model(val lines: List<String>)

}
