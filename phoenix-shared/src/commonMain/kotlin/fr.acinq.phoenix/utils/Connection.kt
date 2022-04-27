import fr.acinq.lightning.utils.Connection

operator fun Connection.plus(other: Connection) : Connection =
    when {
        this == other -> this
        this == Connection.ESTABLISHING || other == Connection.ESTABLISHING -> Connection.ESTABLISHING
        this is Connection.CLOSED -> this
        other is Connection.CLOSED -> other
        else -> error("Cannot add [$this + $other]")
    }