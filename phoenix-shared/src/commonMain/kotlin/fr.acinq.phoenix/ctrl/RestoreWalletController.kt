package fr.acinq.phoenix.ctrl

typealias RestoreWalletController = MVI.Controller<RestoreWallet.Model, RestoreWallet.Intent>

object RestoreWallet {

    sealed class Model : MVI.Model() {
        object Ready : Model()

        data class FilteredWordlist(val words: List<String>) : Model() {
            override fun toString(): String = "FilteredWordlist"
        }

        object InvalidMnemonics : Model()
        data class ValidMnemonics(val seed: ByteArray) : Model() {

            // Kotlin recommends equals & hashCode for data classes with array props
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as ValidMnemonics
                if (!seed.contentEquals(other.seed)) return false
                return true
            }

            override fun hashCode(): Int {
                return seed.contentHashCode()
            }

            override fun toString() = "ValidMnemonics"
        }
    }

    sealed class Intent : MVI.Intent() {
        data class FilterWordList(val predicate: String) : Intent() {
            override fun toString() = ".".repeat(predicate.length)
        }
        data class Validate(val mnemonics: List<String>) : Intent() {
            override fun toString() = "Validate"
        }
    }

}
