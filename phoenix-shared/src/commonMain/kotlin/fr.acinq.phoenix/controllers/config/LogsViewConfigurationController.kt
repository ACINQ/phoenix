//package fr.acinq.phoenix.app.ctrl.config
//
//import fr.acinq.phoenix.app.ctrl.AppController
//import fr.acinq.phoenix.ctrl.config.LogsViewConfiguration
//import fr.acinq.phoenix.utils.LogMemory
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.kodein.log.LoggerFactory
//import org.kodein.memory.file.name
//import org.kodein.memory.file.openReadableFile
//import org.kodein.memory.file.resolve
//import org.kodein.memory.text.readString
//import org.kodein.memory.use
//
//@OptIn(ExperimentalCoroutinesApi::class)
//class AppLogsViewConfigurationController(val fileName: String, loggerFactory: LoggerFactory, logMemory: LogMemory)
//    : AppController<LogsViewConfiguration.Model, LogsViewConfiguration.Intent>(
//        loggerFactory,
//        LogsViewConfiguration.Model.Loading(logMemory.directory.resolve(fileName).path)
//    ) {
//
//    init {
//        launch {
//            if (fileName == logMemory.file.name) {
//                logMemory.rotate().join()
//            }
//            val filePath = logMemory.directory.resolve(fileName)
//            val str = withContext(Dispatchers.Default) {
//                filePath.openReadableFile().use { it.readString() }
//            }
//            model(LogsViewConfiguration.Model.Content(filePath.path, str))
//        }
//    }
//
//    override fun process(intent: LogsViewConfiguration.Intent) = error("Nothing to process")
//}
