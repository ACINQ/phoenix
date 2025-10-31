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
        host = "btc.cihar.com", publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs0gd2ZsghxUZNwjY6cAD" +
                "eZRRvk4sGUvkp5SEENNotiwCFPWXdNxCWxh1aiXpLc/h1+1NmwDHDhFXZDZNGFEW" +
                "GPjW92uZWlcGVZffJWqc8XAvVmTKXUgCDv5daEtyTxk/69NDmmDWSeltV8020ykD" +
                "FcU5cE/xEmBCfFRoR6yIGwIsCQAIX7XnfbDg1+JdN2N3ZSOOlY4B9r7n3Pm0Q0MW" +
                "kRykSFk8EEQYmtk383aFZVDuvUkgLLFsBb0zmkWEVrm6Jy1hXyfWqdsrLaipqhy7" +
                "2n62mHT9vfKhTGIoOXR989v6FA+EIYAklIL2ptX3vLqqvOnRjB122b9eT5ZpZhNi" +
                "uwIDAQAB"
    ),
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
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAu1H7Mbtz/YJKdmCaKeaK" +
            "VUJ6G2VoWOo9yHZkJZHncc5mz/pN7f5QK3mJDakwaXTw64RY3w2P3SLhnujQDYtS" +
            "oVX9K278xv8yrJBNg6ts0UsORkdzqjxJ2FcoLB4r1HcYxaPYEw8hM4h7nGsZ7gHk" +
            "StTtaqIAmBNb4dv/kDPQ2HpdXTzJLIABkFL+BFW3nYrcKxYpZZDgn1kTMQhEIZYb" +
            "t5azXqVUVgR/ZRfu+2BliN5nPTLns0XrGS4tVPa00U68lOVje/wXv0HESdAbyl5h" +
            "sSTcb81djECD2uLk1oQo2N+/KAPaJnwPCLxpSolr8RUNXYDdwKdPz7vNngTw55GO" +
            "R+AIaeLXDFBZzHv+bsYOtyFhc5PUjhldaMIG72CbF6uTc1nEhLQdpJ0WTuDTT29D" +
            "uP4AI7D+nS5wJ9OSW7GA3gPUB5sKv/AWEhRgqqgJDQDw+zKYx++P9VZ0Ebk/chXK" +
            "j8IdusF8UsEkRy4TAONpgssIMVfb793lXnFP4/vEzUmxHEglLJ63r9eUtA7kph4f" +
            "KqIwnSPGFQ2NB37AqsXSdLeb0zcBPJ+rZK7TO9aUfG3toLcoQ1moi0k6J6dreTis" +
            "By3WGCPnSVAnq3vVJyvABfcJQuhXoQ+nF48e3XbfQ1Gnj1Gy6ZB7ikTMOU1KTYzL" +
            "A8FCjlZR1zNWRA+jHppqMcsCAwEAAQ=="
    ),
//    electrumServer(host = "electrum.hodlister.co"), too slow
//    electrumServer(host = "electrum3.hodlister.co"), too slow
//    electrumServer(host = "electrum5.hodlister.co"), too slow
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
    electrumServer(host = "blockstream.info", port = 700),
    electrumServer(host = "alviss.coinjoined.com",
        publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAr8Bmk8+FLFdI9IF+6jZW" +
                "pQ2He/8XuWvHgkI7gBylsLtprca+mRvs2ejZef/s6daffy70faQM4ub6NZx+Mg53" +
                "l/R3TAjQwPvu/3fVJfrUG9JvLvYBuBBoB/uKsFHXoa6OyEy8qd81Lvnx3vOb+DFA" +
                "JD2snJ110yjF4XmXXukdptmO/MsjVVEn1VX0kPORVrP6JC/W79+zskaulbOpeFC6" +
                "0j3Ss0a5blglUhVyJ49LZy+TvxXhss37VGkZak33Cz3oDEWMnvNWWMUmTdyDMDHH" +
                "z4hjsP6ltTWbfci6d99YmtW4fxMmcO7TlBka69Oq0QGhMxRtrwEUjDr6OUsI7GtL" +
                "vwIDAQAB"),
    electrumServer(host = "bitcoin.aranguren.org",
        publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA9IpKA+SdtQIQRcXPFBp0" +
                "XxgqOYWW6U1u7e/fOPrz+NF5gzwvhrmH7arRUrlztkhLGtUr2NmhbG6trBiQ95Ye" +
                "+VnGfgU3IXfnpsvXMOA5PCR3tJGpf40jD1fMYtF7gjdDYQlDYOxvuZg1KkZSJK76" +
                "Td8bGhrsDRkNMUi77dc0v+1ooTSL8Ha71aRgJzHRv4bOulxsZNteQW9nFeHnGklK" +
                "hm5xW+uDyo+gf5+1376c2sMupFOjPTzQT3EU3NAskTrNUNqJM/aXTzU9+pKw7DZU" +
                "e6g6ovN+mKTecFEcmLOnZOrwFDOmTP+x17gfKr/ECMgZzPhY0PiLLJKfDdEdoIiq" +
                "gQIDAQAB"),
    electrumServer(host = "bitcoins.sk", port = 56002,
        publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtJBnSJ2+KWS5lwrnP0mC" +
                "zDcTwFozzGiLgrf72JupKBHFl2nuZUA98INgBlWljtpF8k6StBysTiYS5LFJ7u37" +
                "6oqlRTFjMT0oy8431y7mBq2QEgPzSJ9VqxM24FPnUEL/EyHB7JgzU77ACKGYa/Jz" +
                "X/S1W9ip0J9Rauc6/f/3c7mB6vlrbt3uREdEKOjJhQkLouDQgNSYw9ZKr7iS6Ruw" +
                "RzbAmT/MPg9zwFrPmh3KhlGRXnQowddY9z7M/a9CZ0JwjaG3abDBH5IdkMDIWY85" +
                "8pNjz3dTQUSfOimH/uRehee6x4xLFFMUxtpdkQCq8SEWlLhGBr6MRxkQZeoTR+F8" +
                "NwIDAQAB"),
    electrumServer(host = "btc.electroncash.dk", port = 60002,
        publicKey = "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAv76pDl+g6Bo6kEXs6a94" +
                "1KTF4jTqXSTdy8XbZ83KeFsH9yOd6mj/x0JjOjmvvcygLnf3ka5PJIKZnbAv6tu0" +
                "oi2WJ8IgYmKxwkvoHjXF5Fcuow35t6pNqRxrc6K4kmI/t0CeJCb/oNjWV1n86vua" +
                "glrFBL/IvWptldmXXyXOsssd1Bh/GTILAX+CzaFtBlNZ/Z7AjhqH9g9ltlNddPc1" +
                "QYn1w/aUWTLXyXmOoOGKveScr3ioUQbDu1L2Llp6zGQbi0LRHKPEdtNeIyaoWMhE" +
                "rdBKlbHMDhsyXas1iDjFBP06Cd7oJeHI0oc5XIMSArJKB7NFfAswvmE2HB+H/gWK" +
                "Dot/nrHRZyZUB7DT3D/GK+ONfIQYCxgGd+SZ8RLC4/wVEN6rkyMFHaoHmXYmpCSR" +
                "uLz8LOf04Bjy9J0IFgVP7hvGC05l00lNopMiZ69AkOq1+PgfAlW+0zQLlo/88v0a" +
                "moxLby5+VrPs5MUMBEBDUjQFfFyJMX5PfZWtrMOdNQMFEnJ5NCSztJdyXXpPqvEf" +
                "5SD0NEC2qR+YUPqWS/vg0uccjoIrGJXMW2r2d/9jVATMTlx50djlhy+dF6j3iTRj" +
                "ti/pZHDJmeLcTw8JSKxvtPpds6dd0uWnFGI4BkzlYpIOTsjqKhWlHLDzrSGlWr5e" +
                "Z2JEj+rj0OqRAdpjDjWsNqECAwEAAQ=="),
    electrumServer(host = "electrum.bitaroo.net", publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsvNavM/O2kDINV+iADAc" +
            "Y6HVFmxtr7yQlXKjET/a7Lor/Ih5LQ2Xp/2AUADqBtmxFJwBkkq2uYtmCdzRDwce" +
            "n37b4jMxaiE7hgnDfs5HvYavuDPIL/n00cFqy6+50yAt2xc53bNQFRRouT1lF0/E" +
            "rftC8wYQS3XXfZeNDQCrXhpJHHm356NDC2VtFnZJPDiZOl5BZr2WTUvRRkaV0Hrs" +
            "XUs1vF8xNJPmbtzd4tak4xqVUPpQbXDVEyM6QsJA8cDVZ3bsRS9uN8yzHMBX3cps" +
            "MrM+59dxPzh7JIdEdEJM8RsePqCT7fJB3RO5CyaqqdbI8HaoHbSK2flF68Tq2x16" +
            "RwIDAQAB"),
    electrumServer(host = "electrum.diynodes.com", port = 50022),
    electrumServer(host = "fulcrum.grey.pw", port = 51002),
    electrumServer(host = "fulcrum.sethforprivacy.com", port = 50002,
        publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyHfrzqvuz0nrDCFfVRMx" +
            "E8XXG8a5rSyTqZx/rA3OmqFv4IAP8/j+CzWvgXyvuzjN0eloPouXqQGjXpBacspc" +
            "wZ35coJhGOHwHJVeGL94tE8ELwP0GhiPX3/HOyPy5YjCo7c1HzN7YFc0Epf32kfp" +
            "x8G/eMP9m321VYG9LaErk/aNikrfSRAmA+8rGetGRXoft2yU7I4WS7q6SdhW1Dvh" +
            "xhgF4PviVW8JT9eEBoedyjybNjhiTx+/AaBuYagtnUiHK+Mm3GjfMv8iqvxgLxef" +
            "LMlLJ0H5y3mByO1xFGXGHKckT9G/yu9nSiYe+SmXI+T6BpAlvXwKfb1suZLT3gXv" +
            "vwIDAQAB"),
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
