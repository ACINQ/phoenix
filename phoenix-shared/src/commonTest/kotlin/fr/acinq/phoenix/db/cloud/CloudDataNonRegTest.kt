package fr.acinq.phoenix.db.cloud

import fr.acinq.secp256k1.Hex
import org.kodein.memory.file.*
import org.kodein.memory.system.Environment
import org.kodein.memory.text.putString
import org.kodein.memory.text.readString
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudDataNonRegTest {

    /**
     * If test doesn't pass:
     * - set debug to `true`
     * - run the test again
     * - compare `actual.json` file next to `data.json` in resources
     */
    fun regtest(type: String, subtype: String, debug: Boolean) {
        Path(Environment.findVariable("TEST_RESOURCES_PATH")!!)
            .resolve("nonreg", "cloudData", type, subtype)
            .listDir() // list all test cases
            .forEach { path ->
                val bin = path.resolve("data.bin.hex").openReadableFile().run {
                    Hex.decode(readString(sizeBytes = remaining))
                }
                val ref = path.resolve("data.json").openReadableFile().run {
                    readString(sizeBytes = remaining)
                }
                val state = CloudData.cborDeserialize(bin)!!
                val json = state.jsonSerialize()
                val tmpFile = path.resolve("actual.json")
                if (debug) {
                    tmpFile.openWriteableFile().run {
                        putString(json)
                        close()
                    }
                }
                // deserialized data must match static json reference file
                assertEquals(ref, json, path.toString())
                if (debug) {
                    tmpFile.delete()
                }
            }
    }

    @Test
    fun `non-reg test - incoming - INVOICE_V0`() {
        regtest("incoming", "INVOICE_V0", debug = false)
    }

    @Test
    fun `non-reg test - outgoing - CLOSING_V0`() {
        regtest("outgoing", "CLOSING_V0", debug = false)
    }

    @Test
    fun `non-reg test - outgoing - NORMAL_V0`() {
        regtest("outgoing", "NORMAL_V0", debug = false)
    }

    @Test
    fun `non-reg test - outgoing - SWAPOUT_V0`() {
        regtest("outgoing", "SWAPOUT_V0", debug = false)
    }
}