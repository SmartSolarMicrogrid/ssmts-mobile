package com.ssmts.mobile.ui.reservations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.ModifyReservationRequest
import com.ssmts.mobile.data.remote.ReservationDto
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityReservationDetailBinding
import com.ssmts.mobile.databinding.DialogModifyReservationBinding
import com.ssmts.mobile.util.TimeUtil
import kotlinx.coroutines.launch

/**
 * Full reservation detail with modify and cancel actions.
 * BR-02/BR-03: changes and cancellations close 12h before slot start —
 * enforced server-side and reflected via canModify / canCancel flags.
 */
class ReservationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReservationDetailBinding
    private var reservation: ReservationDto? = null
    private val reservationId by lazy { intent.getStringExtra(EXTRA_ID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnModify.setOnClickListener { showModifyDialog() }
        binding.btnCancel.setOnClickListener { confirmCancel() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.getReservation(reservationId) }) {
                is ApiResult.Success -> render(result.data)
                is ApiResult.Error -> {
                    Toast.makeText(this@ReservationDetailActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun render(r: ReservationDto) {
        reservation = r
        binding.txtReservationNo.text = r.reservationNo
        binding.txtStatus.text = r.status
        binding.txtNode.text = r.nodeName
        binding.txtSlot.text = TimeUtil.slotRange(r.slotStartUtc, r.slotEndUtc)
        binding.txtTrade.text = r.tradeType
        binding.txtKwh.text = "${r.requestedKwh} kWh"
        binding.txtUnitPrice.text = "Rs. %.2f / kWh".format(r.unitPrice)
        binding.txtValue.text = "Rs. %,.2f".format(r.estimatedValue)
        binding.txtDeadline.text = "Changes allowed until ${TimeUtil.dateTime(r.changeDeadlineUtc)}"

        binding.btnModify.visibility = if (r.canModify) View.VISIBLE else View.GONE
        binding.btnCancel.visibility = if (r.canCancel) View.VISIBLE else View.GONE

        // Completed transfers show final settlement details.
        val tx = r.transaction
        if (tx != null) {
            binding.cardTransaction.visibility = View.VISIBLE
            binding.txtMeter.text = "Meter ${tx.meterStartKwh} → ${tx.meterEndKwh} kWh"
            binding.txtActual.text = "Actual: ${tx.actualKwh} kWh · Value: Rs. %,.2f".format(tx.value)
        } else {
            binding.cardTransaction.visibility = View.GONE
        }
    }

    private fun showModifyDialog() {
        val r = reservation ?: return
        val dialogBinding = DialogModifyReservationBinding.inflate(LayoutInflater.from(this))
        dialogBinding.inputKwh.setText(r.requestedKwh.toString())
        if (r.tradeType.equals("Import", true)) {
            dialogBinding.toggleTrade.check(dialogBinding.btnImport.id)
        } else {
            dialogBinding.toggleTrade.check(dialogBinding.btnExport.id)
        }

        AlertDialog.Builder(this)
            .setTitle("Modify reservation")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val kwh = dialogBinding.inputKwh.text?.toString()?.toDoubleOrNull()
                if (kwh == null || kwh < 0.5) {
                    Toast.makeText(this, "Enter a valid kWh amount (min 0.5)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val trade = if (dialogBinding.toggleTrade.checkedButtonId == dialogBinding.btnImport.id)
                    "Import" else "Export"
                applyModify(kwh, trade)
            }
            .setNegativeButton("Discard", null)
            .show()
    }

    private fun applyModify(kwh: Double, trade: String) {
        lifecycleScope.launch {
            when (val result = safeApi {
                ApiClient.api.modifyReservation(
                    reservationId,
                    ModifyReservationRequest(newRequestedKwh = kwh, newTradeType = trade)
                )
            }) {
                is ApiResult.Success -> {
                    Toast.makeText(this@ReservationDetailActivity,
                        "Reservation updated.", Toast.LENGTH_SHORT).show()
                    render(result.data)
                }
                is ApiResult.Error ->
                    Toast.makeText(this@ReservationDetailActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancel booking")
            .setMessage("Cancel this reservation and release the bay?")
            .setPositiveButton("Yes, cancel") { _, _ ->
                lifecycleScope.launch {
                    when (val result = safeApi { ApiClient.api.cancelReservation(reservationId) }) {
                        is ApiResult.Success -> {
                            Toast.makeText(this@ReservationDetailActivity,
                                "Reservation cancelled.", Toast.LENGTH_SHORT).show()
                            load()
                        }
                        is ApiResult.Error ->
                            Toast.makeText(this@ReservationDetailActivity,
                                result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Keep booking", null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "extra_reservation_id"
    }
}
