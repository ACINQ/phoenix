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

package fr.acinq.phoenix.legacy.send

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.common.base.Strings
import com.google.zxing.*
import com.google.zxing.client.android.Intents
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.FragmentReadInvoiceBinding
import fr.acinq.phoenix.legacy.utils.AlertHelper
import fr.acinq.phoenix.legacy.utils.ClipboardHelper
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.lnurl.LNUrlAuth
import fr.acinq.phoenix.legacy.lnurl.LNUrlError
import fr.acinq.phoenix.legacy.lnurl.LNUrlPay
import fr.acinq.phoenix.legacy.lnurl.LNUrlWithdraw
import fr.acinq.phoenix.legacy.utils.*
import fr.acinq.phoenix.legacy.utils.customviews.ButtonView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class ReadInputFragment : BaseFragment() {

  private var manualInputDialog: AlertDialog? = null
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

    model.inputState.observe(viewLifecycleOwner, {
      when (it) {
        is ReadInputState.Scanning -> mBinding.scanView.resume()
        is ReadInputState.Reading -> {
          if (manualInputDialog?.isShowing == true) manualInputDialog?.dismiss()
          mBinding.scanView.pause()
        }
        is ReadInputState.Error -> {
          mBinding.errorMessage.text = when (it) {
            is ReadInputState.Error.PayToSelf -> getString(R.string.legacy_scan_error_pay_to_self)
            is ReadInputState.Error.InvalidChain -> getString(R.string.legacy_scan_error_invalid_chain)
            is ReadInputState.Error.PaymentExpired -> getString(R.string.legacy_scan_error_expired)
            is ReadInputState.Error.ErrorInLNURLResponse -> when (it.error) {
              is LNUrlError.RemoteFailure.Code -> if (it.error.code == 404) {
                Converter.html(getString(R.string.legacy_scan_error_lnurl_failure_code_404, it.error.origin, it.error.code))
              } else {
                Converter.html(getString(R.string.legacy_scan_error_lnurl_failure_code, it.error.origin, it.error.code))
              }
              is LNUrlError.RemoteFailure.Detailed -> Converter.html(getString(R.string.legacy_scan_error_lnurl_failure_detailed, it.error.origin, it.error.reason))
              is LNUrlError.RemoteFailure.Unreadable -> Converter.html(getString(R.string.legacy_scan_error_lnurl_failure_unreadable, it.error.origin))
              is LNUrlError.RemoteFailure -> Converter.html(getString(R.string.legacy_scan_error_lnurl_failure_generic, it.error.origin))
              is LNUrlError.UnhandledTag -> getString(R.string.legacy_scan_error_lnurl_unsupported)
              else -> getString(R.string.legacy_scan_error_invalid_scan)
            }
            is ReadInputState.Error.UnhandledLNURL -> getString(R.string.legacy_scan_error_lnurl_unsupported)
            is ReadInputState.Error.UnhandledInput -> getString(R.string.legacy_scan_error_invalid_scan)
            is ReadInputState.Error.OnChain -> getString(R.string.legacy_scan_error_onchain)
          }
          mBinding.scanView.pause()
        }
        is ReadInputState.Done.Lightning -> {
          // additional controls
          if (app.state.value?.getNodeId() == it.pr.nodeId()) {
            log.debug("abort payment to self")
            model.inputState.value = ReadInputState.Error.PayToSelf
          } else if (it.pr.isExpired) {
            model.inputState.value = ReadInputState.Error.PaymentExpired
          } else if (!Wallet.isSupportedPrefix(it.pr)) {
            model.inputState.value = ReadInputState.Error.InvalidChain
          } else if (it.pr.amount().isEmpty && !it.pr.features().allowTrampoline()) {
            // Payment request is pre-trampoline and SHOULD specify an amount. Show warning to user.
            AlertHelper.build(layoutInflater, Converter.html(getString(R.string.legacy_scan_amountless_legacy_title)), Converter.html(getString(R.string.legacy_scan_amountless_legacy_message)))
              .setCancelable(false)
              .setPositiveButton(R.string.legacy_scan_amountless_legacy_confirm_button) { _, _ -> findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = PaymentRequest.write(it.pr))) }
              .setNegativeButton(R.string.legacy_scan_amountless_legacy_cancel_button) { _, _ ->
                mBinding.scanView.resume()
                model.inputState.value = ReadInputState.Scanning
              }
              .show()
          } else {
            findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = PaymentRequest.write(it.pr)))
          }
        }
        is ReadInputState.Done.Url -> {
          when (it.url) {
            is LNUrlWithdraw -> findNavController().navigate(ReadInputFragmentDirections.actionReadInputToLnurlWithdraw(it.url))
            is LNUrlAuth -> findNavController().navigate(ReadInputFragmentDirections.actionReadInputToLnurlAuth(it.url))
            is LNUrlPay -> findNavController().navigate(ReadInputFragmentDirections.actionReadInputToLnurlPay(it.url))
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

    mBinding.inputButton.setOnClickListener {
      manualInputDialog = AlertHelper.buildWithInput(layoutInflater,
        title = getString(R.string.legacy_send_input_dialog_title),
        message = getString(R.string.legacy_send_input_dialog_message),
        callback = {
          if (it.isNotBlank()) {
            model.readInput(it)
          }
        },
        defaultValue = "",
        hint = getString(R.string.legacy_send_input_dialog_hint)
      ).show()
    }

    mBinding.pasteButton.setOnClickListener {
      context?.let { model.readInput(ClipboardHelper.read(it)) }
    }

    mBinding.browseButton.setOnClickListener {
      startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
      }, Constants.INTENT_PICK_IMAGE_FILE)
    }

    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }

    mBinding.errorButton.setOnClickListener {
      if (model.inputState.value is ReadInputState.Error) {
        model.inputState.value = ReadInputState.Scanning
      }
    }

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

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == Constants.INTENT_PICK_IMAGE_FILE && resultCode == Activity.RESULT_OK) {
      readBitmap(data?.data)
    }
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

  private fun readBitmap(uri: Uri?) {
    lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("failed to load or read QR code image from uri=$uri: ", e)
      model.inputState.postValue(ReadInputState.Error.UnhandledInput)
    }) {
      val bitmap = requireContext().contentResolver.openFileDescriptor(uri!!, "r")?.use {
        BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
      }!!
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      val binaryBitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
      val result = QRCodeMultiReader().decodeMultiple(
        binaryBitmap
      )
      log.info("successfully read QR code with result=$result")
      withContext(Dispatchers.Main) {
        model.readInput(result.first().text)
      }
    }
  }

}
