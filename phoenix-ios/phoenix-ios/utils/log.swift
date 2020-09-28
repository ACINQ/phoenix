import os

extension Logger {
    init<V>(from: V.Type) {
        self.init(subsystem: "fr.acinq.phoenix.app.view", category: String(describing: from))
    }
}
