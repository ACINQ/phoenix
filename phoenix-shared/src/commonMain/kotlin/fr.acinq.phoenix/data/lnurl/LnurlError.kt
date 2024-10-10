/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.data.lnurl

import io.ktor.http.*

sealed class LnurlError(override val message: String? = null) : RuntimeException(message) {
    val details: String by lazy { "Lnurl error=${message ?: this::class.simpleName ?: "N/A"} in url=" }

    sealed class Auth(override val message: String?) : LnurlError(message) {
        object MissingK1 : Auth("missing k1 parameter")
    }

    sealed class Withdraw(override val message: String?) : LnurlError(message) {
        object MissingK1 : Withdraw("missing k1 parameter in auth metadata")
    }

    sealed class Pay : LnurlError() {
        sealed class Intent(override val message: String?) : LnurlError(message) {
            object InvalidMin : Intent("invalid minimum amount")
            object MissingMax : Intent("missing maximum amount parameter")
            object MissingMetadata : Intent("missing metadata parameter")
            data class InvalidMetadata(val meta: String) : Intent("invalid meta=$meta")
        }

        sealed class Invoice(override val message: String?) : LnurlError(message) {
            abstract val origin: String

            data class Malformed(
                override val origin: String,
                val context: String
            ) : Invoice("malformed: $context")

            data class InvalidAmount(override val origin: String) : Invoice("paymentRequest.amount doesn't match user input")
        }
    }

    data class Invalid(override val cause: Throwable?) : LnurlError("cannot be parsed as a bech32 or as a human readable lnurl")
    object NoTag : LnurlError("no tag field found")
    data class UnhandledTag(val tag: String) : LnurlError("unhandled tag=$tag")
    object UnsafeResource : LnurlError("resource should be https")
    object MissingCallback : LnurlError("missing callback in metadata response")

    sealed class RemoteFailure(override val message: String) : LnurlError(message) {
        abstract val origin: String

        data class IsWebsite(override val origin: String) : RemoteFailure("this appears to just be a website")
        data class LightningAddressError(override val origin: String) : RemoteFailure("service $origin doesn't support lightning addresses, or doesn't know this user")
        data class CouldNotConnect(override val origin: String) : RemoteFailure("could not connect to $origin")
        data class Unreadable(override val origin: String) : RemoteFailure("unreadable response from $origin")
        data class Detailed(override val origin: String, val reason: String) : RemoteFailure("error=$reason from $origin")
        data class Code(override val origin: String, val code: HttpStatusCode) : RemoteFailure("error code=$code from $origin")
    }
}
