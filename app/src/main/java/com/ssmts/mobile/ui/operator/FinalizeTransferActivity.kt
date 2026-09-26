package com.ssmts.mobile.ui.operator

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.FinalizeTransferRequest
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityFinalizeTransferBinding
import com.ssmts.mobile.util.TimeUtil
import kotlinx.coroutines.launch

/**
 * Finalize an in-progress energy transfer with meter readings.
 * BR-13: value = actualKwh × snapshotted unit price (server computed).
 */
class FinalizeTransferActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFinalizeTransferBinding
    private val reservationId by lazy { intent.getStringExtra(EXTRA_ID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinalizeTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnFinalize.setOnClickListener { submit() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.getReservation(reservationId) }) {
                is ApiResult.Success -> {
                    val r = result.data
                    binding.txtReservationNo.text = r.reservationNo
                    binding.txtProsumer.text = "Prosumer NIC: ${r.prosumerNic}"
                    binding.txtNode.text = r.nodeName
                    binding.txtSlot.text = TimeUtil.slotRange(r.slotStartUtc, r.slotEndUtc)
                    binding.txtTrade.text =
                        "${r.tradeType} · requested ${r.requestedKwh} kWh @ Rs. %.2f/kWh".format(r.unitPrice)
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@FinalizeTransferActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun submit() {
        val start = binding.inputMeterStart.text?.toString()?.toDoubleOrNull()
        val end = binding.inputMeterEnd.text?.toString()?.toDoubleOrNull()

        binding.layoutMeterStart.error = null
        binding.layoutMeterEnd.error = null

        if (start == null) { binding.layoutMeterStart.error = "Start reading required"; return }
        if (end == null) { binding.layoutMeterEnd.error = "End reading required"; return }
        if (end <= start) { binding.layoutMeterEnd.error = "End must be greater than start"; return }

        binding.btnFinalize.isEnabled = false
        lifecycleScope.launch {
            when (val result = safeApi {
                ApiClient.api.finalizeTransfer(reservationId, FinalizeTransferRequest(start, end))
            }) {
                is ApiResult.Success -> {
                    val tx = result.data.transaction
                    binding.cardResult.visibility = View.VISIBLE
                    binding.txtResult.text = if (tx != null) {
                        "Actual energy: ${tx.actualKwh} kWh\nSettlement value: Rs. %,.2f".format(tx.value)
                    } else {
                        "Transfer completed."
                    }
                    AlertDialog.Builder(this@FinalizeTransferActivity)
                        .setTitle("Transfer completed")
                        .setMessage(binding.txtResult.text)
                        .setPositiveButton("Done") { _, _ -> finish() }
                        .show()
                }
                is ApiResult.Error -> {
                    binding.btnFinalize.isEnabled = true
                    Toast.makeText(this@FinalizeTransferActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "extra_reservation_id"
    }
}
