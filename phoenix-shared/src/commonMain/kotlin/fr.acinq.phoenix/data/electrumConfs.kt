package fr.acinq.phoenix.data

import fr.acinq.eklair.io.TcpSocket

data class ElectrumConf(
    val host: String,
    val pruning: String = "-",
    val sslPort: Int = 50002,
    val tcpPort: Int = 50001,
    val version: String = "1.4"
)

fun ElectrumConf.asElectrumServer(tls: TcpSocket.TLS? = null): ElectrumServer =
    ElectrumServer(host = host, port = if (tls != null) sslPort else tcpPort)

val electrumMainnetConfigurations = listOf(
    ElectrumConf(host = "electrum.acinq.co"),
    ElectrumConf(host = "helicarrier.bauerj.eu"),
    ElectrumConf(host = "e.keff.org"),
    ElectrumConf(host = "e2.keff.org"),
    ElectrumConf(host = "e3.keff.org"),
    ElectrumConf(host = "e8.keff.org"),
    ElectrumConf(host = "electrum-server.ninja"),
    ElectrumConf(host = "electrum-unlimited.criptolayer.net"),
    ElectrumConf(host = "electrum.qtornado.com"),
    ElectrumConf(host = "fortress.qtornado.com"),
    ElectrumConf(host = "enode.duckdns.org"),
    ElectrumConf(host = "bitcoin.dragon.zone",  sslPort = 50004, tcpPort = 50003),
    ElectrumConf(host = "ecdsa.net",  sslPort = 110),
    ElectrumConf(host = "e2.keff.org"),
    ElectrumConf(host = "electrum.hodlister.co"),
    ElectrumConf(host = "electrum3.hodlister.co"),
    ElectrumConf(host = "electrum4.hodlister.co"),
    ElectrumConf(host = "electrum5.hodlister.co"),
    ElectrumConf(host = "electrum6.hodlister.co")
)

val electrumTestnetConfigurations = listOf(
    ElectrumConf(host = "hsmithsxurybd7uh.onion", sslPort= 53012, tcpPort=53011),
    ElectrumConf(host = "testnet.hsmiths.com", sslPort= 53012, tcpPort=53011),
    ElectrumConf(host = "testnet.qtornado.com", sslPort= 51002, tcpPort=51001),
    ElectrumConf(host = "testnet1.bauerj.eu"),
    ElectrumConf(host = "tn.not.fyi", sslPort= 55002, tcpPort=55001),
    ElectrumConf(host = "bitcoin.cluelessperson.com", sslPort= 51002, tcpPort=51001)
)

expect fun platformElectrumRegtestConf(): ElectrumConf
