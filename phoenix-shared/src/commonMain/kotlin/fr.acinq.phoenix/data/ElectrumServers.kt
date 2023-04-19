package fr.acinq.phoenix.data

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress


private fun electrumServer(host: String, port: Int = 50002): ServerAddress =
    ServerAddress(host = host, port = port, tls = TcpSocket.TLS.TRUSTED_CERTIFICATES())

private fun electrumServer(host: String, port: Int = 50002, publicKey: String): ServerAddress =
    ServerAddress(host = host, port = port, tls = TcpSocket.TLS.PINNED_PUBLIC_KEY(publicKey))

val mainnetElectrumServers = listOf(
    electrumServer(host = "electrum.acinq.co"),
    electrumServer(
        host = "E-X.not.fyi", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAyVwW8xbiNJet4n23lSQ1" +
                "SNZZ+3izTuxatOZKzhdvD8+k46fVq+vLK5cCu3ezd5BiP6PnmjoKetGTrgzsvfK3" +
                "e3bAFfFWR89JrVv176zID+56yPq38J7bJKU3P3KgRalDuEA6D8xV30m8Sg/hyBik" +
                "tlF/WqFssKkzjZ4mcImsJPAUZ1pEJCNm7WCR53ch8ZOjlYDY3Jrmev4eQ9m32PpM" +
                "B9QhkJjWPRyUrdkoViTbW38j2rXRYqniaiHBJDt/7qxCl2bevnhItMQ7ytLKGilm" +
                "Wkr2v2vJ2er+6I26/+YkebfqyxATuUEZInVcwqJlYueW/Iuk7k4GimYBskCAyCt4" +
                "bfBBg3296NW6cmEi5JEk0MQvZe/kiT6FRAs28cSmDsE5BDyIWxmAg7wbSiDJfA2C" +
                "ZLPIwswUGzyvbp2//KCFZyatkqwZlFlKV7W2aS5eVjBVCrg0seBLukzlhDdl0mkC" +
                "lrc8zJLmfj6bThewcZNXwIqf3dxmUpq5dJx/7IRIfNBGQmYKy+Z2roWJZYsILXCH" +
                "iM5cg4qjZaDahCiBD8RHjU1NbuMhKmc/2LO9wIIaK0msCg8Y0zogecs0y7VQMUxg" +
                "HDxoTXjRk56q9CvAimA/aB7uCChWZ9BLn9ykEOe4eVK+96CEbnwaF10NzVUabYIW" +
                "o3tlo0x9iOONU+QoTaOLP6kCAwEAAQ=="
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
    electrumServer(host = "e.keff.org"),
    electrumServer(host = "tardis.bauerj.eu", publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEArnwocjdtIf+Pfpw0DAW2" +
                "QQlQUA3VV7s/ttr0KHrDFwnvhfm6odxfUgW9REfxvxcp7xRJsp2ovFZzKj6uwOWg" +
                "43Ewr0sTvOjyPmJdVMHAxpDCmm6BcF4cD7zs9OZfYsz5Yv0tewN9yh6DBt6fPkXT" +
                "/inDAvInnGJJNhKfBaz7ixPR5w1JJJufnGq0SZqwfysXdhidF+eSAR7YAGjEwxaQ" +
                "O827wAe/lsE59sNv75b01sSlMaQmusIfsIZ8Sezo/zdTmE+MYS178oRYcQboMY/o" +
                "PMAqEuQ1rbu6YlvvIQOTPcBdgM1mJbov0Uu8manO6jZSaQDx/Vn7liG8/SMjuP7A" +
                "ySl/eJtFMzgosbCMjxLqfG4737X4oZp8/1KqCDYsY5OtBZYkqHNSO0Uv5I3lPH6/" +
                "mE6LemZsIQs9+NT+jPSgNNcqnrlX5/hhTB7hWxNj/y/m0MsSi9IKjLU2tRJP5P5R" +
                "FpnUWGuW4F+0MdBRwBwt0ar8uy/2mHxNCn62X6vtBDIjCVkI/Kl54PjmOLyUmbpr" +
                "F4l7hYM8ZOtxq4pi1Xjy/e2uBCDcaogAik1ciLFSrR2ai33eevi3YcTiB0DjLK9z" +
                "DUx5CoQGwyhJcRopglpX+AFQry659ngRnvxdFJYmUH7CQQ/ofvdkOZ6m4hv1tVVn" +
                "3GtwazLKQKgN6dKyHuuaj0ECAwEAAQ=="),
    electrumServer(host = "VPS.hsmiths.com", publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAx6SVh9svGg1cYMn4uxvt" +
                "D9XS1IS/TSWV+1vZkovG1Hk5b8451B0hN48ZpzeWiMozxNPncBIVYsDw37YZkDGw" +
                "yn4Akb1vFdgJtKiJrDKFVhLKPMZncOWQjnt9qcip04G7aTe2RqmUlLCTDbgr9G3T" +
                "DLXx3kNSBHD0fiA/DTR2W8t2BfwlpEZiA2ZPZ2CgmQX6TXE1jfCP8mDE81jsnta4" +
                "FVnCgh3Bj4zp8bhxnWd4S2T40FEsoX8uLeYp/HzcQJu0LDXOOTGpANskc+GA+fC9" +
                "WYnDFMQ1p502zyRXOszEsMMQbqT8xfyE8oG+Ge6yUWeD0DDli0YRvDISxt54jE6k" +
                "YQIDAQAB"
    ),
    electrumServer(host = "bitcoin.aranguren.org", publicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA9IpKA+SdtQIQRcXPFBp0" +
                "XxgqOYWW6U1u7e/fOPrz+NF5gzwvhrmH7arRUrlztkhLGtUr2NmhbG6trBiQ95Ye" +
                "+VnGfgU3IXfnpsvXMOA5PCR3tJGpf40jD1fMYtF7gjdDYQlDYOxvuZg1KkZSJK76" +
                "Td8bGhrsDRkNMUi77dc0v+1ooTSL8Ha71aRgJzHRv4bOulxsZNteQW9nFeHnGklK" +
                "hm5xW+uDyo+gf5+1376c2sMupFOjPTzQT3EU3NAskTrNUNqJM/aXTzU9+pKw7DZU" +
                "e6g6ovN+mKTecFEcmLOnZOrwFDOmTP+x17gfKr/ECMgZzPhY0PiLLJKfDdEdoIiq" +
                "gQIDAQAB"
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
    electrumServer(
        host = "tn.not.fyi", port = 55002, publicKey =
                "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA7/z3CDZ2d6gJ/7mEt9yI" +
                "9KNKxs5dbwzhKB99uYdWp1QogMHEo9Jg+uYGg2M7UhO76vOFaf1Hp2gstd1Q0n2G" +
                "Lwtq3a0JSam3EIaC7QuX8V190IexyPSVdwaPtPBzN4KCZd8smfiR01oMJkhCH2MK" +
                "aCrJKL7PJrSZMmrLMzo5yYTnHtBZVR9gL0T5bJeO3NTNuQBV1ft6kYaKa30d5zMO" +
                "7j/CkzclQkh6d07RrS/YnkZNswfnqKdEqNNGa3FfuZ8vwSwRtpEQRAdkjxSFsLSo" +
                "Iv+HoTx1nFhbmaqdZasFIxmn9Zp30KMb328k5j9fszcAn78y7tVodiddODBMgGSp" +
                "ofq+nJDkNlKD0NVNLML50IB2U2BL/62+7c2RguvLYljNkTSB3HhF+b6DiGLZDbg4" +
                "8ea12Hr+9Nxf4lW+hIKO3s/88MMlrB2lORORiuFtmiqYiiTc5YzJPXzXGhoCCmgQ" +
                "xij6XYi0v4bpFr8wANtDobZhziWZSHm6T+u5RsSSkMGHc6hJaZmVg9xGznsD7RmR" +
                "a+Wmf/Tl4sY0hI+nea4YyrN7+i1KT04EA9HrG6nBUKgKJkp2OciKmGGrINmdSCvk" +
                "Z9laCxcGpEuAL0sI/WalmAXDTm/rjNdMZ0AClD3gCweqsblKZ07Ewtzh0DE65B7v" +
                "tZWgWZM/wz4dnMYwY9VIrcUCAwEAAQ=="
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

expect fun platformElectrumRegtestConf(): ServerAddress
