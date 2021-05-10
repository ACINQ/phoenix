/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import fr.acinq.eclair.`package$`
import fr.acinq.phoenix.db.LNUrlPayActionData
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import fr.acinq.bitcoin.ByteVector32
import org.junit.Assert
import scodec.bits.ByteVector


@RunWith(AndroidJUnit4::class)
@SmallTest
class LNUrlPayTest {
  @Test
  fun decrypt_aes_action() {
    val mySecret = "sic transit gloria mundi"
    val iv = `package$`.`MODULE$`.randomBytes32().bytes().take(16)
    val preimage = ByteVector32.fromValidHex("c0f9b692a068450e9362bf1688b1d3dd46132c7540b4f23f44047dcb49931d01")
    val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(preimage.bytes().toArray(), "AES"), IvParameterSpec(iv.toArray()))
    val action = LNUrlPayActionData.Aes.V0(
      description = "this is a description",
      iv = ByteVector.view(iv.toArray()).toBase64(),
      cipherText = ByteVector.view(aesCipher.doFinal(mySecret.toByteArray(Charsets.UTF_8))).toBase64()
    )
    Assert.assertEquals(mySecret, action.decrypt(preimage))
  }
}
