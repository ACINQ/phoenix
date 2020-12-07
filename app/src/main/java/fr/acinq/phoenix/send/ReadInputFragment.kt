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

package fr.acinq.phoenix.send

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.common.base.Strings
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentReadInvoiceBinding
import fr.acinq.phoenix.lnurl.LNUrlAuth
import fr.acinq.phoenix.lnurl.LNUrlWithdraw
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.customviews.ButtonView
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReadInputFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentReadInvoiceBinding
  private val args: ReadInputFragmentArgs by navArgs()
  private lateinit var model: ReadInputViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentReadInvoiceBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(ReadInputViewModel::class.java)
    mBinding.model = model

    model.inputState.observe(viewLifecycleOwner, Observer {
      when (it) {
        is ReadInputState.Scanning -> mBinding.scanView.resume()
        is ReadInputState.Reading -> mBinding.scanView.pause()
        is ReadInputState.Error -> {
          mBinding.errorMessage.text = getString(when (it) {
            is ReadInputState.Error.PayToSelf -> R.string.scan_error_pay_to_self
            is ReadInputState.Error.InvalidChain -> R.string.scan_error_invalid_chain
            is ReadInputState.Error.PaymentExpired -> R.string.scan_error_expired
            is ReadInputState.Error.UnhandledLNURL -> R.string.scan_error_lnurl_unsupported
            is ReadInputState.Error.UnhandledInput -> R.string.scan_error_invalid_scan
          })
          mBinding.scanView.pause()
          Handler().postDelayed({
            if (model.inputState.value is ReadInputState.Error) {
              model.inputState.value = ReadInputState.Scanning
            }
          }, 1750)
        }
        is ReadInputState.Done.Lightning -> {
          // check payment request chain
          val acceptedPrefix = PaymentRequest.prefixes().get(Wallet.getChainHash())
          // additional controls
          if (app.state.value?.getNodeId() == it.pr.nodeId()) {
            log.debug("abort payment to self")
            model.inputState.value = ReadInputState.Error.PayToSelf
          } else if (it.pr.isExpired) {
            model.inputState.value = ReadInputState.Error.PaymentExpired
          } else if (acceptedPrefix.isEmpty || acceptedPrefix.get() != it.pr.prefix()) {
            model.inputState.value = ReadInputState.Error.InvalidChain
          } else if (it.pr.amount().isEmpty && !it.pr.features().allowTrampoline()) {
            // Payment request is pre-trampoline and SHOULD specify an amount. Show warning to user.
            AlertHelper.build(layoutInflater, Converter.html(getString(R.string.scan_amountless_legacy_title)), Converter.html(getString(R.string.scan_amountless_legacy_message)))
              .setCancelable(false)
              .setPositiveButton(R.string.scan_amountless_legacy_confirm_button) { _, _ -> findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = PaymentRequest.write(it.pr))) }
              .setNegativeButton(R.string.scan_amountless_legacy_cancel_button) { _, _ ->
                mBinding.scanView.resume()
                model.inputState.value = ReadInputState.Scanning
              }
              .show()
          } else {
            findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = PaymentRequest.write(it.pr)))
          }
        }
        is ReadInputState.Done.Onchain -> {
          val uri = it.bitcoinUri
          if (it.bitcoinUri.lightning != null) {
            val view = layoutInflater.inflate(R.layout.dialog_payment_mode, null)
            val payOnChain = view.findViewById<ButtonView>(R.id.paymode_onchain_button)
            val payOnLightning = view.findViewById<ButtonView>(R.id.paymode_lightning_button)
            val dialog = AlertDialog.Builder(context, R.style.default_dialogTheme)
              .setView(view)
              .setCancelable(false)
              .create()
            payOnChain.setOnClickListener {
              findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = uri.raw))
              dialog.dismiss()
            }
            payOnLightning.setOnClickListener {
              findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = PaymentRequest.write(uri.lightning)))
              dialog.dismiss()
            }
            dialog.show()
          } else {
            findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = it.bitcoinUri.raw))
          }
        }
        is ReadInputState.Done.Url -> {
          when (it.url) {
            is LNUrlWithdraw -> findNavController().navigate(ReadInputFragmentDirections.actionReadInputToLnurlWithdraw(it.url))
            is LNUrlAuth -> findNavController().navigate(ReadInputFragmentDirections.actionReadInputToLnurlAuth(it.url))
            else -> model.inputState.value = ReadInputState.Error.UnhandledLNURL
          }
        }
      }
    })
  }

  override fun onStart() {
    super.onStart()
    val barcodeIntent = Intent()
    barcodeIntent.putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
    barcodeIntent.putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.name)
    mBinding.scanView.statusView.visibility = View.GONE
    mBinding.scanView.initializeFromIntent(barcodeIntent)

    if (!Strings.isNullOrEmpty(args.payload)) {
      model.readInput(args.payload!!)
    }

    mBinding.cameraAccessButton.setOnClickListener {
      activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.CAMERA), Constants.INTENT_CAMERA_PERMISSION_REQUEST) }
    }

    mBinding.pasteButton.setOnClickListener {
      context?.let { model.readInput(ClipboardHelper.read(it)) }
    }

    mBinding.cancelButton.setOnClickListener { findNavController().popBackStack() }

    activity?.let { activity ->
      if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        model.hasCameraAccess.value = false
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), Constants.INTENT_CAMERA_PERMISSION_REQUEST)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    context?.let {
      if (ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        model.hasCameraAccess.value = true
        startScanning()
        mBinding.scanView.resume()
      }
    }
  }

  override fun onPause() {
    super.onPause()
    mBinding.scanView.pause()
  }

  private fun startScanning() {
    if (model.inputState.value is ReadInputState.Error || model.inputState.value is ReadInputState.Done) {
      model.inputState.postValue(ReadInputState.Scanning)
    }
    mBinding.scanView.decodeContinuous(object : BarcodeCallback {
      override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit

      override fun barcodeResult(result: BarcodeResult?) {
        result?.text?.let { model.readInput(it) }
      }
    })
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    if (requestCode == Constants.INTENT_CAMERA_PERMISSION_REQUEST) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        model.hasCameraAccess.value = true
        startScanning()
      }
    }
  }

}
