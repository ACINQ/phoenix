package fr.acinq.phoenix.data

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress


private fun electrumServer(host: String, port: Int = 50002): ServerAddress =
    ServerAddress(host = host, port = port, tls = TcpSocket.TLS.TRUSTED_CERTIFICATES())

private fun electrumServer(host: String, port: Int = 50002, publicKey: String): ServerAddress =
    ServerAddress(host = host, port = port, tls = TcpSocket.TLS.PINNED_PUBLIC_KEY(publicKey))

private fun electrumServerOnion(host: String, port: Int = 50002): ServerAddress =
    ServerAddress(host = host, port = port, tls = TcpSocket.TLS.DISABLED)

val mainnetElectrumServers = listOf(
    electrumServer(host = "electrum.acinq.co"),
    electrumServer(
        host = "E-X.not.fyi", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA8wmp3hyau0aAOjszUUJY" +
                "YcMlDqlQ0/Gi7xYf0id1CG+e0yjU2pHuPXgnEmtXdsLIF5GleU7LP5L1xPrzGQD3" +
                "LZb8CGKcl7Ve9H167wt5kiehJ/AaF4xcL96uaGQ8ykZMxZrz01AD72mT7u9S7IJt" +
                "ypdHbiSq9YiTQj/lscYw318woRV/VLf9qaPfANileffEDRuOJB4OT6FizB+1CDoD" +
                "ayI8J7sEiPPYuV7/ttNIEGH6wQCQLxQHQAP6fkAAQ+WMuwl2UeG7NvDocHZJp5hA" +
                "L0LJkgquH4LaoFzzA2Yh61Ep1uWeRH7KKlXQnhPRUkgUKfrgovhT5kyszIIpkZiZ" +
                "Z2g15fXTRVp2WBxJSa9qPEgg310T37KFXwaV08XdnEEa7pz5oHYUcUPGlRWuYDVQ" +
                "X7HAUYvwT84eRvEj+E6L3FhsI0EulzvaHUO8SvKjSK94yoG6FepFi95eNdAwIUXg" +
                "LgOWKu/zsdCoDWbaA9nihIJw9ZPESbb8q1WDAOV+M6YLcAyE0hLWDzra3euxUAuB" +
                "vIc/tP8RkJ1tzrHE+3KosNAO7y8mP4XlnPvkY5ZS01VXL6a+NoPcDL3+tZr1jjb4" +
                "XBjxhaQKn4EhlvwTURL9VOoZADOnVU+DmGiTRJbKNIL84yio/nIubKrD4eONggUh" +
                "QDEcaySiP9R+yKz5C9o/WlUCAwEAAQ=="
    ),
    electrumServer(
        host = "btc.cihar.com", publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs0gd2ZsghxUZNwjY6cAD" +
                "eZRRvk4sGUvkp5SEENNotiwCFPWXdNxCWxh1aiXpLc/h1+1NmwDHDhFXZDZNGFEW" +
                "GPjW92uZWlcGVZffJWqc8XAvVmTKXUgCDv5daEtyTxk/69NDmmDWSeltV8020ykD" +
                "FcU5cE/xEmBCfFRoR6yIGwIsCQAIX7XnfbDg1+JdN2N3ZSOOlY4B9r7n3Pm0Q0MW" +
                "kRykSFk8EEQYmtk383aFZVDuvUkgLLFsBb0zmkWEVrm6Jy1hXyfWqdsrLaipqhy7" +
                "2n62mHT9vfKhTGIoOXR989v6FA+EIYAklIL2ptX3vLqqvOnRjB122b9eT5ZpZhNi" +
                "uwIDAQAB"
    ),
    electrumServer(host = "e.keff.org"), // certificate has expired
    electrumServer(
        host = "tardis.bauerj.eu", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAxLHijYCSNS9SDOgcMPo3" +
                "ldVzRTo3LYwozUOUI2P5P8ip7sLFmXPjLbRdzSaKi6YA1J56muie5MJgAimqPo8F" +
                "vclOGQrArpU/mQEHbWBZJyPQiftldILRLGAN5OpZnAilLtNuPOtbqbEn5KtX7hyz" +
                "K4Xq+RZd32PMpehVVpG9LZTL/QCB5m99iffUl5uR3BX36siOJIWpahPMizzJKdP0" +
                "RNZcAKrx5YdWStUYtfprjfBDKXQN6SB5tOVHxVLPpQz3+Iv1mab2nBbQxqiuPTyW" +
                "8KC1ZsaLfvnQdBgnWxSPcSuLmlc4hsjloC0oinUnH3j4MvqkaTrTsokUMF4ROHiX" +
                "ks9UcbvzdTXHh9c+8Ia/fVLLAIZitKrc9glFKI1hkjFRAQeGlc3m4TFcsT8ue8a6" +
                "C6btxW1XZ/BPhznpk9FdUtU0BjZKtwg12cuqSfBcqdFgIwN60jM5N16n1hQDrMHN" +
                "eZuW5DcVBIR4gq8eZUZ15460Ck4qufliWFD/M6G7rO+hLOIxu9MEe6r5CF1bGaNw" +
                "mNJhIZeg3JGN9fn1h7kX4VW5H+9v7YVpYGB/vGXCDEsOLjHlZVjgbtungBVJ1UML" +
                "uOixmccwKWZecT5jXCypj671lzOxF6edUbRsD2xXFjkG5RoifqyNY6zM08bNTyVo" +
                "BbQJhHwGCCFBV/e5RgVATIUCAwEAAQ=="
    ),
    electrumServer(
        host = "VPS.hsmiths.com", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwaNR7gST8KEAwc7tGTZP" +
                "1T6qVhg+KjqY1LpvkZ8m1QBPh+j6HsTjB4+RiH1MerqMWVLD9dEZEYZsEUREySyb" +
                "G3szQ1nf+GZkn3XorKyvX29B0aZow3ZdAKwTXF51jwCE/G7j3UpteKIYoX/egoUq" +
                "G8k/N3q0jxUZXGEY33BhHGAdiuRAk70QDIUjh1zuPHVED9tE9fc/qeJ/ZlQbQHzK" +
                "Y820JcqR1JingTufVps26SyiOHO+jzlRGsO3qHpAmtc1ikEJt1dPS53Rcu8LojF0" +
                "kgKZ5oCgGSCWU/+T+8xffp/y5GrV7lxlFNNwM2HFLvx24r2ovYujMzd45VgQ+rIG" +
                "V1FcAVDXsarNtSV2R5YmyldScRnzR5IEHcPWPFXMuMvZR31COtD99pXsoS60JO9S" +
                "Bd7DMcbU/aXxlyfltla58JS3RV27Uem01YkIfx9d0QW07TXPRmWfQNQ1aZV4Iyfn" +
                "oWJnYRR36BOI3TIZKGBSFYvvojNh6noeRVp/BCDRmsi4lL3PLAAH3jKJ2jGjXnZ7" +
                "uQNlxsC6ZDL9yezndEjpRYQPYZLrUGOOuNKJReSHtWw83U6wd/5TyI8NRGkHZlCi" +
                "585YChjthLYR8fdfHR2sAvMf0pfqBQgV8sNQ3qIZdpaVZIr0wXucsKDGhsZO3Zym" +
                "FlzYsP0snim4LMNIlQ+W8N8CAwEAAQ=="
    ),
    electrumServer(
        host = "electrum.qtornado.com", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAm78Y1pLds3BzsHpo9Bz2" +
                "2lzu9tS/7loMcdL6AVJ3zVgGycI5whOWuaQntG0aYSiHammZNgGzjv44oU/PluzX" +
                "PhMxzPlNgSEHnVi2K9mzG7HuGMQh5tEJfvt1zoMmnV4qTaZSLgKKcvrG9112LGQF" +
                "UZBhV2J3gN21c8rqj/b6NfEqtItKVU173nqYchYu1GBGrHK1nDWWwTVrtHzY4Qos" +
                "etyCPPnj/JWkO6I8kW2CIevpbXh5PROb+YvdbIsqvRODFgo1oLHmwf09Y7ZxE/nS" +
                "LZ8yI67U1O39EcRQnoob6pbaqbaoNr0KZSR3xXcJTz2RkhlErMVNH850QGxlXMOr" +
                "wNKgVrHhUFEBklbsk11Mx+mYWoeHrvZn55xwpTYGaZmZmAVwwUvherz/Tg490XD2" +
                "2+2T78Zu3mmfmHKfD9uhC+ewyn+REHiz9vrvmMeh+YMBOEwf8lp1Jqte7/8xgdfq" +
                "kDguOkN6azG64+LyzgWrB79Dfql858Rwn+ezpBZKcSyIL7o1r0T/WeCuR6vLiQ8q" +
                "YbwAm61pUM9aVshda2WGRRUkhpy7Uj5OHpEQsnXqtoeEzTuiCr1y19VJgwabcUQH" +
                "MVN1HGHiBl5eU4xBhDUG0l/268Ulk2lcRSI0udRtu7jjQzhSnKQL6HUhCm7PCdXg" +
                "SqOSwa1yPuHtg/rNBXcqK8cCAwEAAQ=="
    ),
    electrumServer(
        host = "electrum.emzy.de", publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAufqDv0nJICJPoP86wOPY" +
                "M/XIfFs6vmVrGEeBZmy9MmMNubulhnyE+sWuFhnIX+0uuFlJ18LJYFOIg0fAaFdN" +
                "5xh4vagBNXP9dCcxfVfMGfv9xBQZfWhKrDjC4DCJ82n3K+Q4RqpK/yS9GIZIcqrG" +
                "3rxELBZ8NHVPIXveW0PnagGeQ31NDdOAq9MBKiKRcmfKem5daUEo/xM4nTt+tOTx" +
                "l5sHicdQSCePHXewMXzmkM/Vw1rMJZKeTwnLX44TsppEi47fXUFcduB2+A1xHQIg" +
                "E9wa4Bqc2ZoUtKKBayeeU02C2SBFgVxtAWT6YESdcPP8u+pR7lADA7QZNUVNKMvM" +
                "NwIDAQAB"
    ),
    electrumServer(
        host = "ecdsa.net", port = 110, publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzGHtaM37N0tgS7xcWmSq" +
                "S2eOHBOtsZIGkkccbQbdmkgSpMGZSJgr23OofrlhmcdzFIzlqK2nXii7+5KW3Lgs" +
                "zHKt9pArroFRRRFvoWHp5ijWYRATo7GKVPCngCPbg4fl4wxPYp9yg14BPe7BJfSN" +
                "Stz6gV+akcCmKMVfqN5JFqPuuzOmSib270TgHCtIUccgDqHdP1muPQWZjCCxjePT" +
                "f6e6J478Bh+Eop4lqvpEiLGeU/6Qj2oZ2tmO7j09J6Ycp0FHBISuCWWUCuZmIEk/" +
                "NGOIUagggRU2tVFeW6wjSm1T3Q/z/b6G5oIaldZnklqo//79d3B4Fjj+C8lnFhpP" +
                "/wIDAQAB"
    ),
    electrumServer(host = "electrum.hodlister.co"),
    electrumServer(host = "electrum3.hodlister.co"),
    electrumServer(host = "electrum5.hodlister.co"),
    electrumServer(
        host = "fortress.qtornado.com", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAy7xI+hVU3rUSAyMMX8OI" +
                "LdCs5m5Az1rzFL/8MTSnCBUnXY+YjmN8vFg8ktc/ebwSnjxCfjjXjPPcf0qSS2Xa" +
                "WZ3M7nAlzYLpZCTuGBWE2ZbQ30QzXiMzh3Ucum4EJUeGOTEDtisdCrv3jjDs/P96" +
                "UgR8MMA1fJqIpxAKnbEj5I9FX9t4eFVhYl6aiykJUFpV3aUZiDsqH8v5MiXCWHAc" +
                "EGx1nfzY/os5xYYm/w0+VJiyX6HN0AViA2zF1wnWZFRqwCWpffaZAAPQdYaJ+bGl" +
                "zR2PQneczw01EfJwMgb8QfMy3GbZFga7gT5NcZGzEAztL8Y7EzhtjcLksvhm5TB7" +
                "iZbvNlCTmyv6F/jm90sZZAwYsg+9Dzl5ciJTKigQnMbPlDaOiRUKXkdw114Q2emO" +
                "XKxs74fBl2g/XNO+3Jt7LSDrHEN38ygrHROxPbCTJmUawQ29KUBDULlnsWgzb9Ni" +
                "uWMdaPJTXPUQSEYcux1jkVED1l5vANds0bTgM3fipb1MhvFb2GYJiVJkwu342fiG" +
                "ADGpTzzjMqhsxevph8zh72MdaxyHCLlvk5OswTEoGNdo6ZFRBpmWDJF9zMnXCPEM" +
                "vk2PS8RRlECPNORxSDsu79uXVm0+VmhLshcLOEEb+9qhLid063YLZrwEqZJshbm9" +
                "Gcc9FdI7SUP85+RR9XnyL9cCAwEAAQ=="
    ),
    electrumServer(host = "electrum.blockstream.info"),
    electrumServer(host = "blockstream.info", port = 700)
)

val testnetElectrumServers = listOf(
    electrumServer(
        host = "testnet.qtornado.com", port = 51002, publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwkLgqNkkTbwpV3gMdgDA" +
                "+jJFdzrOp8vIDT/qxVIox8NZ53pxPc2N44aeY1NJx4TyfpHUGcI7l+gxZfLr8a13" +
                "o3CIQSotDbZZdJhS6Ir5tT4iRqwZJch+HayTQf9rztv8OQWgrflWDzCiYtBA5PGx" +
                "6LEQWyah/xPPUbeANe/ndEzlfAhXjNcynSfrkikzTgFNBqnc5CcTkHjYgzCXqMwy" +
                "ZCD6kQTQG+eqIHSHul21dwUougfCWCR+P0zFA7LeUfPz2mLZktmGXjqTyYZ+0ZTU" +
                "gJz/MMZt9PDWGJZsHQzoFSCMicukKtnvZ4Q0gbPOoYp8+WjD4SH+WmC3MZdLagsi" +
                "05hUDdm7PHIM1VHQTALLGRnW3yTOaqhsvYGAM5UOkDcmgUqIr6IztHGWCKldfbhS" +
                "c4l7BIgvwW2M6FxYlSAcavIodNfvEC1ythdMzl8bZsBjGIOZ39WtiM0grgcg7bb8" +
                "W5ovZpLOXpzZBjS0zB0sZJnumjS+3jCSjy9rZXGUn3JmMdqtTV8RQxkB8OBJhFf5" +
                "qtMSZXiJIr9RH71VoJKjnds/hoILHuCKU3HOJeo0+4KSD8+q4g3tZLr/haIrsHg5" +
                "uifT9db6tDML1PTKpbHkW+f3w9PdhSmsNUUXrgNmQ0MoBhxV7U2Qcug3jX3xaf1P" +
                "gwWDg3nZZizhuvBceY0IYLECAwEAAQ=="
    ),
    electrumServer(host = "testnet1.electrum.acinq.co", port = 51002),
    electrumServer(host = "blockstream.info", port = 993),
    electrumServer(
        host = "testnet.aranguren.org", port = 51002, publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+RL/AH7wn08YKlRCswER" +
                "M0JBdGycRuGBgQbM0guzDxi7Ov01WB9AM0DX7GC09pAOefbRP8QzXEhyKO0qpnin" +
                "+5Vz4jKS+xv4zPGx2MpTqjJxzom/6v13cumZxWXMzVSeNUTjVp2sOPQ1JQaHqqjs" +
                "2lTgShWu+pAgKUH1KPWxMSz21cI+AQkT8NuuXe0USYYIeiXzyTpciIaBf50j6185" +
                "u+4bUwA3hvdPZyrkJDtSluJ0HiJzCSFlmNYNHLqbvZNAYrgUM3qJRTsvmD0JK6mm" +
                "8m7iXW4m6mKX22VgR93meD/3rdcrJ8FbMbVlkS3wimzcYezls9JytaXupyeRKhQj" +
                "TQIDAQAB"
    ),
)

val mainnetElectrumServersOnion: List<ServerAddress> by lazy {
    listOf(
        electrumServerOnion(host = "22mgr2fndslabzvx4sj7ialugn2jv3cfqjb3dnj67a6vnrkp7g4l37ad.onion", port = 50001),
        electrumServerOnion(host = "bejqtnc64qttdempkczylydg7l3ordwugbdar7yqbndck53ukx7wnwad.onion", port = 50001),
        electrumServerOnion(host = "egyh5mutxwcvwhlvjubf6wytwoq5xxvfb2522ocx77puc6ihmffrh6id.onion", port = 50001),
        electrumServerOnion(host = "explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion", port = 110),
        electrumServerOnion(host = "kittycp2gatrqhlwpmbczk5rblw62enrpo2rzwtkfrrr27hq435d4vid.onion", port = 50001),
        electrumServerOnion(host = "qly7g5n5t3f3h23xvbp44vs6vpmayurno4basuu5rcvrupli7y2jmgid.onion", port = 50001),
        electrumServerOnion(host = "rzspa374ob3hlyjptkdgz6a62wim2mpanuw6m3shlwn2cxg2smy3p7yd.onion", port = 50003),
        electrumServerOnion(host = "ty6cgwaf2pbc244gijtmpfvte3wwfp32wgz57eltjkgtsel2q7jufjyd.onion", port = 50001),
        electrumServerOnion(host = "udfpzbte2hommnvag5f3qlouqkhvp3xybhlus2yvfeqdwlhjroe4bbyd.onion", port = 60001),
        electrumServerOnion(host = "v7gtzf7nua6hdmb2wtqaqioqmesdb4xrlly4zwr7bvayxv2bpg665pqd.onion", port = 50001),
        electrumServerOnion(host = "v7o2hkemnt677k3jxcbosmjjxw3p5khjyu7jwv7orfy6rwtkizbshwqd.onion", port = 57001),
        electrumServerOnion(host = "venmrle3xuwkgkd42wg7f735l6cghst3sdfa3w3ryib2rochfhld6lid.onion", port = 50001),
        electrumServerOnion(host = "wsw6tua3xl24gsmi264zaep6seppjyrkyucpsmuxnjzyt3f3j6swshad.onion", port = 50001),
    )
}

val testnetElectrumServersOnion by lazy {
    listOf(
        electrumServerOnion(host = "explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion", port = 143)
    )
}

expect fun platformElectrumRegtestConf(): ServerAddress
