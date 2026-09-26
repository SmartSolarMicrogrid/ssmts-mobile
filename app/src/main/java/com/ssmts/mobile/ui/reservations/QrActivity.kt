package com.ssmts.mobile.ui.reservations

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityQrBinding
import com.ssmts.mobile.util.QrUtil
import com.ssmts.mobile.util.TimeUtil
import kotlinx.coroutines.launch

/**
 * Secure transaction QR pass for an approved reservation.
 * The payload is HMAC-SHA256 signed by the server (BR-12) and only
 * becomes valid 30 minutes before the slot opens; the 6-digit backup
 * code covers camera-less verification at the station.
 */
class QrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val reservationId = intent.getStringExtra(EXTRA_ID).orEmpty()
        load(reservationId)
    }

    private fun load(reservationId: String) {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.getQr(reservationId) }) {
                is ApiResult.Success -> {
                    val qr = result.data
                    binding.imgQr.setImageBitmap(QrUtil.encode(qr.payload))
                    binding.txtReservationNo.text = qr.reservationNo
                    binding.txtBackupCode.text = qr.backupCode
                    binding.txtValidity.text =
                        "Valid ${TimeUtil.dateTime(qr.validFromUtc)} – ${TimeUtil.dateTime(qr.validToUtc)}"

                    // Lets the operator paste the payload when no camera is available.
                    binding.btnCopyPayload.setOnClickListener {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("SSMTS QR payload", qr.payload))
                        Toast.makeText(this@QrActivity, "Payload copied.", Toast.LENGTH_SHORT).show()
                    }
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@QrActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "extra_reservation_id"
    }
}
