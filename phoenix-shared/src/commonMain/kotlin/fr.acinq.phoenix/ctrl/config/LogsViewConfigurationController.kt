//package fr.acinq.phoenix.ctrl.config
//
//import fr.acinq.phoenix.ctrl.MVI
//
//typealias LogsViewConfigurationController = MVI.Controller<LogsViewConfiguration.Model, LogsViewConfiguration.Intent>
//
//object LogsViewConfiguration {
//
//    sealed class Model : MVI.Model() {
//        abstract val path: String
//        data class Loading(override val path: String) : Model()
//        data class Content(override val path: String, val text: String) : Model()
//    }
//
//    sealed class Intent : MVI.Intent()
//
//}
