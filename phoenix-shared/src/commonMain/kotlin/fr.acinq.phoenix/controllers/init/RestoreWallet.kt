package fr.acinq.phoenix.controllers.init

import fr.acinq.phoenix.controllers.MVI
import fr.acinq.phoenix.utils.MnemonicLanguage

object RestoreWallet {

    sealed class Model : MVI.Model() {
        object Ready : Model()

        data class FilteredWordlist(
            val uuid: String,
            val predicate: String,
            val words: List<String>
        ) : Model() {
            override fun toString(): String = "FilteredWordlist"
        }

        object InvalidMnemonics : Model()
        data class ValidMnemonics(
            val mnemonics: List<String>,
            val language: MnemonicLanguage,
            val seed: ByteArray
        ) : Model() {

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
        data class FilterWordList(
            val predicate: String,
            val language: MnemonicLanguage,
            val uuid: String = "" // See note below
        ) : Intent() {
            // We are using StateFlow to handle model changes.
            // The problem is that StateFlow is conflated,
            // so it will silently drop notifications if the model doesn't change.
            // As per issue #109, we encountered problems with this.
            // For example, if the user pastes in a seed such as "hammer hammer ...",
            // then what happens is:
            //
            // - UI calls intent with FilterWordList(hammer) // 1st word
            // - model is updated to FilteredWordlist([hammer])
            // - UI is notified
            // - UI calls intent with FilterWordList(hammer) // 2nd word
            // - model is updated to FilteredWordlist([hammer])
            // - UI is NOT updated, because the model didn't change !
            //
            // So the uuid is a workaround, to force a model change everytime.
            //
            // Another possible solution is to switch from MutableStateFlow to MutableShareFlow.
            // However, doing so would affect AppController.kt, which effects every MVI.
            // So we're reserving that as a potential future change.

            override fun toString() = ".".repeat(predicate.length)
        }
        data class Validate(
            val mnemonics: List<String>,
            val language: MnemonicLanguage
        ) : Intent() {
            override fun toString() = "Validate"
        }
    }

}
