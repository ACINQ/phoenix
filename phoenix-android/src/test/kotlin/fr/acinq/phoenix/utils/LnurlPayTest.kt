/*
 * Copyright 2023 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.data.lnurl.LnurlPay
import org.junit.Assert
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LnurlPayTest {

    @Test
    fun lnurlpay_decrypt_aes_action() {
        val mySecret = "sic transit gloria mundi"
        val iv = ByteVector.fromHex("8b26f326a41ef49b846a1ef2c92416be")
        val preimage = ByteVector.fromHex("c0f9b692a068450e9362bf1688b1d3dd46132c7540b4f23f44047dcb49931d01")

        // encrypt secret
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(preimage.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
        val action = LnurlPay.Invoice.SuccessAction.Aes(
            description = "this is a description",
            iv = iv,
            ciphertext = cipher.doFinal(mySecret.toByteArray(Charsets.UTF_8)).toByteVector()
        )
        Assert.assertEquals("6a18fda90c47c23666caff87e5f9683e45dd5efbf470d8f72822f13ce1f53f0f", action.ciphertext.toHex())

        // decrypt secret
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(preimage.toByteArray(), "AES"), IvParameterSpec(action.iv.toByteArray()))
        val decrypted = String(cipher.doFinal(action.ciphertext.toByteArray()), Charsets.UTF_8)
        Assert.assertEquals(mySecret, decrypted)
    }
}