package fr.acinq.phoenix.data

actual fun platformElectrumRegtestConf(): ElectrumAddress = ElectrumAddress(host = "127.0.0.1", tcpPort = 51001, sslPort = 51002)
