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
import android.app.Activity
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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSendInitBinding
import fr.acinq.eclair.phoenix.utils.Clipboard
import fr.acinq.eclair.phoenix.utils.IntentCodes

class InitSendFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSendInitBinding

  private lateinit var model: InitSendViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSendInitBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(InitSendViewModel::class.java)
    mBinding.model = model

    model.paymentRequest.observe(this, Observer {
      if (it != null) {
        val action = SendFragmentDirections.globalActionAnyToSend(it)
        findNavController().popBackStack().also { findNavController().navigate(action) } //  navigate(action)
        // go to main!!!
        //        val result = Intent()
        //        result.putExtra(IntentCodes.SEND_PAYMENT_INVOICE_EXTRA, it)
        //        setResult(Activity.RESULT_OK, result)
        //        finish()
      }
    })
    model.readingState.observe(this, Observer {
      if (it == ReadingState.ERROR) {
        Handler().postDelayed({ model.readingState.value = ReadingState.SCANNING }, 850)
      }
    })
  }

  override fun onStart() {
    super.onStart()
    val barcodeIntent = Intent()
    barcodeIntent.putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
    barcodeIntent.putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.toString())
    mBinding.scanView.statusView.visibility = View.GONE
    mBinding.scanView.initializeFromIntent(barcodeIntent)

    mBinding.cameraAccessButton.setOnClickListener {
      activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.CAMERA), IntentCodes.CAMERA_PERMISSION_REQUEST) }
    }

    mBinding.pasteButton.setOnClickListener {
      context?.let { model.checkAndSetPaymentRequest(Clipboard.read(it)) }
    }
    mBinding.cancelButton.setOnClickListener { findNavController().popBackStack() }
  }

  override fun onResume() {
    super.onResume()
    context?.let {
      if (ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        model.hasCameraAccess.value = true
        startScanning()
        mBinding.scanView.resume()
      } else {
        model.hasCameraAccess.value = false
        activity?.let { act ->
          ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.CAMERA), IntentCodes.CAMERA_PERMISSION_REQUEST)
        }
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
