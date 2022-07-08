package fr.acinq.phoenix.data


data class ElectrumAddress(
    val host: String,
    val tlsPort: Int = 50002,
    // DER-encoded publicKey as base64 string.
    // (I.e. same as PEM format, without BEGIN/END header/footer)
    val pinnedPublicKey: String? = null
)

val electrumMainnetConfigurations = listOf(
    ElectrumAddress(host = "electrum.acinq.co"),
    ElectrumAddress(host = "E-X.not.fyi", pinnedPublicKey =
        "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAz4l1L5f4+6VMN/3a3JcI" +
        "qpyBSlpqYevUQ9uJIoKa8zOoEWWfRiRSBW1kcplRybPXRzUTR0mFduFNYsuAw5Kj" +
        "nNQ3BeHRwnu0IxcLmP/+Oj9Q7rdUxHggYwkXJ4K5AUFT07fr6jKIFiwS5z/zXzWV" +
        "9ngzNhieGeUWqZy8YF4OPUQx9Wz5KsFqG7PSS2GYAUe8dLr4VNvgAF46U1yVQetO" +
        "6abJfLdnKzh2EVh3pIpJL3+6ggjpqyNgR+YXEDYSmES3KMwglUf34PKd6rKOQ/sE" +
        "ddRKEJvsxfysWrJvNbbQ7PFOt2czj2x/+Is0iCpvH3UsIe4pQD03CJHywwjgyp6l" +
        "RWWZIeUHJSSq9zDlkX9HqJJcu4cHv9cCGMx8Zsv/DwoDOIitZNKUbNq35PWxStlJ" +
        "Q904A86GV5U52IBRXnF9Zcom6dvp1qchd5TuNnFC+5T+nvFH0NStxsa+b9VBoCDB" +
        "yY6UxL5QEgn+9fZbvXYTtUZSJv/fqUKJY7NPtXRYgKVO5o7FbpF4HIFYBGNhfpw6" +
        "4ps3qHAIXh1tISWT45BsLuTfsvjXsvSoPTDgzGt51zUpHrgEZSXhMrMSmDflIKjR" +
        "g7AjlGkm/MsUTmL6McDn3lOduAas+496FX65vAQY7Ag5GR8yNzrtsLlRsGT2aPLA" +
        "0PGxtnsWR6+sAjsv4VX+Z98CAwEAAQ=="
    ),
//  ElectrumAddress(host = "VPS.hsmiths.com"), // no longer working
    ElectrumAddress(host = "btc.cihar.com", pinnedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs0gd2ZsghxUZNwjY6cAD" +
        "eZRRvk4sGUvkp5SEENNotiwCFPWXdNxCWxh1aiXpLc/h1+1NmwDHDhFXZDZNGFEW" +
        "GPjW92uZWlcGVZffJWqc8XAvVmTKXUgCDv5daEtyTxk/69NDmmDWSeltV8020ykD" +
        "FcU5cE/xEmBCfFRoR6yIGwIsCQAIX7XnfbDg1+JdN2N3ZSOOlY4B9r7n3Pm0Q0MW" +
        "kRykSFk8EEQYmtk383aFZVDuvUkgLLFsBb0zmkWEVrm6Jy1hXyfWqdsrLaipqhy7" +
        "2n62mHT9vfKhTGIoOXR989v6FA+EIYAklIL2ptX3vLqqvOnRjB122b9eT5ZpZhNi" +
        "uwIDAQAB"
    ),
    ElectrumAddress(host = "e.keff.org"),
    ElectrumAddress(host = "electrum.qtornado.com", pinnedPublicKey =
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
    ElectrumAddress(host = "electrum.emzy.de", pinnedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAufqDv0nJICJPoP86wOPY" +
        "M/XIfFs6vmVrGEeBZmy9MmMNubulhnyE+sWuFhnIX+0uuFlJ18LJYFOIg0fAaFdN" +
        "5xh4vagBNXP9dCcxfVfMGfv9xBQZfWhKrDjC4DCJ82n3K+Q4RqpK/yS9GIZIcqrG" +
        "3rxELBZ8NHVPIXveW0PnagGeQ31NDdOAq9MBKiKRcmfKem5daUEo/xM4nTt+tOTx" +
        "l5sHicdQSCePHXewMXzmkM/Vw1rMJZKeTwnLX44TsppEi47fXUFcduB2+A1xHQIg" +
        "E9wa4Bqc2ZoUtKKBayeeU02C2SBFgVxtAWT6YESdcPP8u+pR7lADA7QZNUVNKMvM" +
        "NwIDAQAB"
    ),
//  ElectrumAddress(host = "tardis.bauerj.eu"), // no longer working
    ElectrumAddress(host = "ecdsa.net", tlsPort = 110, pinnedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzGHtaM37N0tgS7xcWmSq" +
        "S2eOHBOtsZIGkkccbQbdmkgSpMGZSJgr23OofrlhmcdzFIzlqK2nXii7+5KW3Lgs" +
        "zHKt9pArroFRRRFvoWHp5ijWYRATo7GKVPCngCPbg4fl4wxPYp9yg14BPe7BJfSN" +
        "Stz6gV+akcCmKMVfqN5JFqPuuzOmSib270TgHCtIUccgDqHdP1muPQWZjCCxjePT" +
        "f6e6J478Bh+Eop4lqvpEiLGeU/6Qj2oZ2tmO7j09J6Ycp0FHBISuCWWUCuZmIEk/" +
        "NGOIUagggRU2tVFeW6wjSm1T3Q/z/b6G5oIaldZnklqo//79d3B4Fjj+C8lnFhpP" +
        "/wIDAQAB"
    ),
    ElectrumAddress(host = "e2.keff.org"),
    ElectrumAddress(host = "electrum3.hodlister.co"),
    ElectrumAddress(host = "electrum5.hodlister.co"),
    ElectrumAddress(host = "fortress.qtornado.com", pinnedPublicKey =
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
    ElectrumAddress(host = "electrumx.erbium.eu", pinnedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7IjEBAKC+2BnsfIekktU" +
        "IzmHyQCB3SLVbvc50Ds3kaUEJX7wi/GxC/SGwVAjlE6zf135ruteTNqd4eJgG6pb" +
        "kvgIImvCdMzfsGc+zupkOkVhImPXbr/mlPMG9hBoIp0yxKlBtOGsZDFuGdhLt6lJ" +
        "navfrxLN3jU0nA9VrlS5cqs2S1WROSSRk2vh/jTQHius7ZQHwdAUSOBZTnIpni03" +
        "wmvWa/hxTR5iRauDuVhkksyBSC8x6aIyON1F5mS29AHzL3uf4qByV6FUIFsE8az6" +
        "bryC5GfdeKRVZdz1Bsd+3HncCdtC3S0x0QAD7rvQJ+NFLBsvyzplqBQZuj7IoFL6" +
        "mwIDAQAB"
    ),
    ElectrumAddress(host = "electrum.bitkoins.nl", tlsPort = 50512),
    ElectrumAddress(host = "electrum.blockstream.info"),
    ElectrumAddress(host = "blockstream.info", 700)
)

val electrumTestnetConfigurations = listOf(
    ElectrumAddress(host = "testnet.qtornado.com", tlsPort= 51002, pinnedPublicKey =
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
    ElectrumAddress(host = "tn.not.fyi", tlsPort= 55002, pinnedPublicKey =
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
    ElectrumAddress(host = "testnet1.electrum.acinq.co", tlsPort = 51002),
    ElectrumAddress(host = "blockstream.info", tlsPort = 993),
    ElectrumAddress(host = "testnet.aranguren.org", tlsPort = 51002, pinnedPublicKey =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+RL/AH7wn08YKlRCswER" +
        "M0JBdGycRuGBgQbM0guzDxi7Ov01WB9AM0DX7GC09pAOefbRP8QzXEhyKO0qpnin" +
        "+5Vz4jKS+xv4zPGx2MpTqjJxzom/6v13cumZxWXMzVSeNUTjVp2sOPQ1JQaHqqjs" +
        "2lTgShWu+pAgKUH1KPWxMSz21cI+AQkT8NuuXe0USYYIeiXzyTpciIaBf50j6185" +
        "u+4bUwA3hvdPZyrkJDtSluJ0HiJzCSFlmNYNHLqbvZNAYrgUM3qJRTsvmD0JK6mm" +
        "8m7iXW4m6mKX22VgR93meD/3rdcrJ8FbMbVlkS3wimzcYezls9JytaXupyeRKhQj" +
        "TQIDAQAB"
    ),
)

expect fun platformElectrumRegtestConf(): ElectrumAddress
