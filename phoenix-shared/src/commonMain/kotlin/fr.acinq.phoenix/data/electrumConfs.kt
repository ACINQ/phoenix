package fr.acinq.phoenix.data

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.ServerAddress

data class ElectrumAddress(
    val host: String,
    val sslPort: Int = 50002,
    val tcpPort: Int = 50001,
    val version: String = "1.4"
) {
    fun asServerAddress(tls: TcpSocket.TLS?): ServerAddress =
        ServerAddress(host, port = if (tls != null) sslPort else tcpPort, tls)
}

val electrumMainnetConfigurations = listOf(
    ElectrumAddress(host = "electrum.acinq.co"),
    ElectrumAddress(host = "helicarrier.bauerj.eu"),
    ElectrumAddress(host = "e.keff.org"),
    ElectrumAddress(host = "e2.keff.org"),
    ElectrumAddress(host = "e3.keff.org"),
    ElectrumAddress(host = "e8.keff.org"),
    ElectrumAddress(host = "electrum-server.ninja"),
    ElectrumAddress(host = "electrum-unlimited.criptolayer.net"),
    ElectrumAddress(host = "electrum.qtornado.com"),
    ElectrumAddress(host = "fortress.qtornado.com"),
    ElectrumAddress(host = "enode.duckdns.org"),
    ElectrumAddress(host = "bitcoin.dragon.zone", sslPort = 50004, tcpPort = 50003),
    ElectrumAddress(host = "ecdsa.net", sslPort = 110),
    ElectrumAddress(host = "e2.keff.org"),
    ElectrumAddress(host = "electrum.hodlister.co"),
    ElectrumAddress(host = "electrum3.hodlister.co"),
    ElectrumAddress(host = "electrum4.hodlister.co"),
    ElectrumAddress(host = "electrum5.hodlister.co"),
    ElectrumAddress(host = "electrum6.hodlister.co")
)

val electrumTestnetConfigurations = listOf(
//    ElectrumAddress(host = "hsmithsxurybd7uh.onion", sslPort= 53012, tcpPort=53011),
//    ElectrumAddress(host = "testnet.hsmiths.com", sslPort= 53012, tcpPort=53011),
//    ElectrumAddress(host = "testnet.qtornado.com", sslPort= 51002, tcpPort=51001),
//    ElectrumAddress(host = "testnet1.bauerj.eu"),
//    ElectrumAddress(host = "tn.not.fyi", sslPort= 55002, tcpPort=55001),
//    ElectrumAddress(host = "bitcoin.cluelessperson.com", sslPort= 51002, tcpPort=51001),
    ElectrumAddress(host = "testnet1.electrum.acinq.co", sslPort = 51002, tcpPort = 51001),
)

expect fun platformElectrumRegtestConf(): ElectrumAddress
