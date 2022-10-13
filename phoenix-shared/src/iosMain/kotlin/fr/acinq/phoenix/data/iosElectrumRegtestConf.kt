package fr.acinq.phoenix.data

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress

actual fun platformElectrumRegtestConf(): ServerAddress = ServerAddress(host = "127.0.0.1", port = 51002, TcpSocket.TLS.DISABLED)
