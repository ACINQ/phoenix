/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.phoenix.initwallet

import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentInitWalletCreateBinding
import fr.acinq.eclair.phoenix.utils.Converter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters

class CreateSeedFragment : InitWalletBaseFragment() {

  private lateinit var mBinding: FragmentInitWalletCreateBinding
  private lateinit var model: CreateSeedViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletCreateBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(CreateSeedViewModel::class.java)
    mBinding.model = model
    model.words.observe(viewLifecycleOwner, Observer { words ->
      mBinding.wordsTable.removeAllViews()
      var i = 0
      while (i < words.size / 2) {
        val row = TableRow(context)
        row.gravity = Gravity.CENTER
        row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        row.addView(buildWordView(i, words[i], true))
        row.addView(buildWordView(i + words.size / 2, words[i + (words.size / 2)], false))
        mBinding.wordsTable.addView(row)
        i += 1
      }
    })
  }

  override fun onDestroyView() {
    super.onDestroyView()
    log.info("CreateSeedFragment.onDestroyView")
  }

  private fun buildWordView(i: Int, word: String, hasRightPadding: Boolean): TextView {
    val bottomPadding = resources.getDimensionPixelSize(R.dimen.space_xxs)
    val rightPadding = if (hasRightPadding) resources.getDimensionPixelSize(R.dimen.space_lg) else 0
    val textView = TextView(context)
    textView.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
    textView.text = Converter.html(getString(R.string.newseed_words_td, i + 1, word))
    textView.setPadding(0, 0, rightPadding, bottomPadding)
    return textView
  }

  override fun onStart() {
    super.onStart()
    mBinding.nextButton.setOnClickListener {
      seedModel.words.value = model.words.value
      findNavController().navigate(R.id.action_create_seed_to_setup_pin)
    }
  }

  class CreateSeedViewModel : ViewModel() {
    private val log = LoggerFactory.getLogger(CreateSeedViewModel::class.java)

    private val _words = MutableLiveData<List<String>>()
    val words: LiveData<List<String>> get() = _words

    val userHasSavedWords = MutableLiveData<Boolean>()
    val userHasAcknowledgedSoleRecourse = MutableLiveData<Boolean>()

    init {
      generateSeed()
      userHasSavedWords.value = false
      userHasAcknowledgedSoleRecourse.value = false
    }

    @UiThread
    fun generateSeed() {
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          val generated: List<String> = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(`package$`.`MODULE$`.randomBytes(16), MnemonicCode.englishWordlist())).asJava()
          _words.postValue(generated)
        }
      }
    }
  }

}
