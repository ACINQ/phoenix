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

package fr.acinq.eclair.phoenix.send

import android.Manifest
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.databinding.FragmentReadInvoiceBinding
import fr.acinq.eclair.phoenix.utils.BitcoinURI
import fr.acinq.eclair.phoenix.utils.Clipboard
import fr.acinq.eclair.phoenix.utils.IntentCodes
import fr.acinq.eclair.phoenix.utils.LNUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReadInputFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentReadInvoiceBinding

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

    model.invoice.observe(viewLifecycleOwner, Observer {
      if (it != null) {
        when {
          it is PaymentRequest -> findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = PaymentRequest.write(it)))
          it is BitcoinURI -> findNavController().navigate(SendFragmentDirections.globalActionAnyToSend(payload = it.raw))
          it is LNUrl && it.isLogin() -> findNavController().navigate(ReadInputFragmentDirections.actionReadInputToLnurlLogin(it.uri.toString()))
          it is LNUrl -> {
            log.info("cannot handle LNurl with uri=${it.uri}")
            model.readingState.postValue(ReadingState.ERROR)
          }
          else -> model.invoice.value = null
        }
      }
    })
    model.readingState.observe(viewLifecycleOwner, Observer {
      when (it) {
        ReadingState.ERROR -> {
          mBinding.scanView.resume()
          Handler().postDelayed({ model.readingState.value = ReadingState.SCANNING }, 1250)
        }
        ReadingState.READING -> {
          mBinding.scanView.pause()
        }
        ReadingState.SCANNING -> {
          mBinding.scanView.resume()
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

    mBinding.cameraAccessButton.setOnClickListener {
      activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.CAMERA), IntentCodes.CAMERA_PERMISSION_REQUEST) }
    }

    mBinding.pasteButton.setOnClickListener {
      context?.let { model.checkAndSetPaymentRequest(Clipboard.read(it)) }
    }
    mBinding.cancelButton.setOnClickListener { findNavController().popBackStack() }
    context?.let {
      if (ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        model.hasCameraAccess.value = false
        activity?.let { act ->
          ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.CAMERA), IntentCodes.CAMERA_PERMISSION_REQUEST)
        }
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
    mBinding.scanView.decodeContinuous(object : BarcodeCallback {
      override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
      }

      override fun barcodeResult(result: BarcodeResult?) {
        val scannedText = result?.text
        if (scannedText != null) {
          model.checkAndSetPaymentRequest(scannedText)
        }
      }
    })
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    if (requestCode == IntentCodes.CAMERA_PERMISSION_REQUEST) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        model.hasCameraAccess.value = true
        startScanning()
      }
    }
  }

}
