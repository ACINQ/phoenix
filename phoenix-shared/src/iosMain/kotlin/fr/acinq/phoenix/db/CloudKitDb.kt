package fr.acinq.phoenix.db

import fr.acinq.phoenix.db.payments.*
import kotlinx.coroutines.*

class CloudKitDb(
    appDb: SqliteAppDb,
    paymentsDb: SqlitePaymentsDb
): CloudKitInterface, CoroutineScope by MainScope() {

    val cards = CloudKitCardsDb(paymentsDb)
    val contacts = CloudKitContactsDb(paymentsDb)
    val payments = CloudKitPaymentsDb(paymentsDb)
}
