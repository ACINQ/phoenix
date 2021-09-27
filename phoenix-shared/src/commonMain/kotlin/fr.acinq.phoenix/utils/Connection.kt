import fr.acinq.lightning.utils.Connection

operator fun Connection.plus(other: Connection) : Connection =
    when {
        this == other -> this
        this == Connection.ESTABLISHING || other == Connection.ESTABLISHING -> Connection.ESTABLISHING
        this == Connection.CLOSED || other == Connection.CLOSED -> Connection.CLOSED
        else -> error("Cannot add [$this + $other]")
    }