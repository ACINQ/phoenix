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
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import fr.acinq.eclair.phoenix.R
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InitWalletActivity : AppCompatActivity() {

  val log: Logger = LoggerFactory.getLogger(InitWalletActivity::class.java)
  private lateinit var mBinding: fr.acinq.eclair.phoenix.databinding.ActivityInitWalletBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_init_wallet)
    ViewModelProviders.of(this).get(SharedSeedViewModel::class.java)
  }
}
