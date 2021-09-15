package fr.acinq.phoenix.data


data class ElectrumAddress(
    val host: String,
    val sslPort: Int = 50002,
    val tcpPort: Int = 50001,
    val version: String = "1.4"
)

val electrumMainnetConfigurations = listOf(
    ElectrumAddress(host = "electrum.acinq.co"),
    ElectrumAddress(host = "E-X.not.fyi"),
    ElectrumAddress(host = "VPS.hsmiths.com"),
    ElectrumAddress(host = "btc.cihar.com"),
    ElectrumAddress(host = "e.keff.org"),
    ElectrumAddress(host = "electrum.qtornado.com"),
    ElectrumAddress(host = "electrum.emzy.de"),
    ElectrumAddress(host = "tardis.bauerj.eu"),
    ElectrumAddress(host = "ecdsa.net", sslPort = 110),
    ElectrumAddress(host = "e2.keff.org"),
    ElectrumAddress(host = "electrum3.hodlister.co"),
    ElectrumAddress(host = "electrum5.hodlister.co"),
    ElectrumAddress(host = "fortress.qtornado.com"),
    ElectrumAddress(host = "electrumx.erbium.eu"),
    ElectrumAddress(host = "electrum.bitkoins.nl", sslPort = 50512),
    ElectrumAddress(host = "electrum.blockstream.info"),
    ElectrumAddress(host = "blockstream.info", 700)
)

val electrumTestnetConfigurations = listOf(
    ElectrumAddress(host = "testnet.qtornado.com", sslPort= 51002),
    ElectrumAddress(host = "tn.not.fyi", sslPort= 55002),
    ElectrumAddress(host = "testnet1.electrum.acinq.co", sslPort = 51002),
    ElectrumAddress(host = "blockstream.info", sslPort = 993),
    ElectrumAddress(host = "testnet.aranguren.org", sslPort = 51002),
)

expect fun platformElectrumRegtestConf(): ElectrumAddress
