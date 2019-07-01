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

package fr.acinq.eclair.phoenix.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.ActivityScanBinding
import fr.acinq.eclair.phoenix.utils.Clipboard
import fr.acinq.eclair.phoenix.utils.IntentCodes
import org.slf4j.LoggerFactory

class ScanActivity : AppCompatActivity() {

  private val log = LoggerFactory.getLogger(ScanActivity::class.java)
  private lateinit var mBinding: ActivityScanBinding
  private lateinit var model: ScanViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_scan)
    mBinding.lifecycleOwner = this
    model = ViewModelProviders.of(this).get(ScanViewModel::class.java)
    mBinding.model = model

    model.paymentRequest.observe(this, Observer {
      if (it != null) {
        val result = Intent()
        result.putExtra(IntentCodes.SEND_PAYMENT_INVOICE_EXTRA, it)
        setResult(Activity.RESULT_OK, result)
        finish()
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
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), IntentCodes.CAMERA_PERMISSION_REQUEST)
    }

    mBinding.pasteButton.setOnClickListener {
      applicationContext?.let {
        model.checkAndSetPaymentRequest(Clipboard.read(applicationContext))
      }
    }
    mBinding.cancelButton.setOnClickListener { finish() }
  }

  override fun onResume() {
    super.onResume()
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      model.hasCameraAccess.value = true
      startScanning()
      mBinding.scanView.resume()
    } else {
      model.hasCameraAccess.value = false
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), IntentCodes.CAMERA_PERMISSION_REQUEST)
    }
  }

  override fun onPause() {
    super.onPause()
    mBinding.scanView.pause()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    return mBinding.scanView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
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

  //  // payment request is generated in appkit view model and updates the receive view model
  //  private fun verifyPaymentRequest(input: String) {
  //    lifecycleScope.launch(CoroutineExceptionHandler{ _, exception ->
  //      log.info("error when verifying payment request: $exception")
  //    }) {
  //      model.paymentRequest.value = appKit.generatePaymentRequest("toto", MilliSatoshi(110000 * 1000))
  //    }
  //  }
}
