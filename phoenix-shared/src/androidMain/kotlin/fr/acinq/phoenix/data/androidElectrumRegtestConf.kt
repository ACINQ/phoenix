package fr.acinq.phoenix.data

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress

actual fun platformElectrumRegtestConf(): ServerAddress = ServerAddress(host = "10.0.2.2", port = 51002, tls = TcpSocket.TLS.DISABLED)
