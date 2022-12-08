package fr.acinq.phoenix.utils

import kotlinx.coroutines.*
import kotlinx.datetime.*
import org.kodein.log.LogFrontend
import org.kodein.log.LogReceiver
import org.kodein.log.Logger
import org.kodein.log.frontend.printLogIn
import org.kodein.memory.file.Path
import org.kodein.memory.file.createDirs
import org.kodein.memory.file.openWriteableFile
import org.kodein.memory.file.resolve
import org.kodein.memory.text.putString
import org.kodein.memory.use
import kotlin.time.Duration.Companion.seconds


class LogMemory(val directory: Path) : LogFrontend, CoroutineScope by MainScope() {

    data class Line(val instant: Instant, val tag: Logger.Tag, val entry: Logger.Entry, val message: String?)

    private var lines = ArrayList<Line>()

    private fun currentDate() = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    val file get() = directory.resolve(toFileName(currentDate()) + ".txt")

    private val threshold = 20
    private val every = 10.seconds
    private var lastRotate = Clock.System.now()

    init {
        directory.createDirs()

        launch {
            while (true) {
                delay(every)
                if (Clock.System.now() - lastRotate >= (every / 2)) {
                    rotate().join()
                }
            }
        }
    }

    inner class Receiver(private val tag: Logger.Tag) : LogReceiver {
        override fun receive(entry: Logger.Entry, message: String?) {
            lines.add(Line(Clock.System.now(), tag, entry, message))
            if (lines.size >= threshold) rotate()
        }
    }

    fun rotate(): Job {
        lastRotate = Clock.System.now()

        if (lines.isEmpty()) {
            return Job().apply { complete() }
        }

        val localLines = this.lines
        this.lines = ArrayList()

        return writeLogs(ArrayList(localLines), file)
    }


    override fun getReceiverFor(tag: Logger.Tag): LogReceiver = Receiver(tag)

    companion object {
        private fun Int.format(): String = toString().padStart(2, '0')

        fun toFileName(date: LocalDate): String =
            with(date) { "$year-${monthNumber.format()}-${dayOfMonth.format()}" }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun writeLogs(lines: List<LogMemory.Line>, file: Path): Job = GlobalScope.launch(Dispatchers.Default) {
    val allLines = buildString {
        lines.forEach { line ->
            printLogIn(line.tag, line.entry, line.message, printer = {
                this.appendLine(it)
            })
        }
    }
    file.openWriteableFile(append = true).use { wf ->
        wf.putString(allLines)
    }
}