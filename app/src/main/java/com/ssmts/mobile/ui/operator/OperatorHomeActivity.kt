package com.ssmts.mobile.ui.operator

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ssmts.mobile.SsmtsApp
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.RejectReservationRequest
import com.ssmts.mobile.data.remote.ReservationDto
import com.ssmts.mobile.data.remote.VerifyTransferRequest
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityOperatorHomeBinding
import com.ssmts.mobile.databinding.DialogBackupCodeBinding
import com.ssmts.mobile.ui.auth.LoginActivity
import com.ssmts.mobile.ui.reservations.ReservationAdapter
import kotlinx.coroutines.launch

/**
 * Grid Operator mode — scan prosumer QR passes, verify against the server
 * (BR-11 node scoping, BR-12 signature/time-window/single-use) and manage
 * pending approvals for assigned stations.
 */
class OperatorHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOperatorHomeBinding
    private val app by lazy { SsmtsApp.from(this) }
    private lateinit var adapter: ReservationAdapter
    private var currentTab = "Pending"

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val payload = result.contents
        if (payload.isNullOrBlank()) return@registerForActivityResult
        verify(VerifyTransferRequest(payload = payload))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOperatorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtGreeting.text = "Operator · ${app.session.name ?: ""}"

        adapter = ReservationAdapter { reservation -> onReservationTap(reservation) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.isNestedScrollingEnabled = false

        binding.btnScan.setOnClickListener {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan the prosumer's transaction QR pass")
                    .setBeepEnabled(true)
                    .setOrientationLocked(true)
            )
        }

        binding.chipPending.setOnClickListener { switchTab("Pending") }
        binding.chipApproved.setOnClickListener { switchTab("Approved") }
        binding.chipInProgress.setOnClickListener { switchTab("InProgress") }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("Sign out of operator mode?")
                .setPositiveButton("Sign out") { _, _ ->
                    app.session.clear()
                    startActivity(Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.swipeRefresh.setOnRefreshListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun switchTab(status: String) {
        currentTab = status
        loadList()
    }

    private fun refresh() {
        loadStats()
        loadList()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.operatorDashboard() }) {
                is ApiResult.Success -> {
                    val d = result.data
                    binding.statPending.text = d.pendingApprovalsCount.toString()
                    binding.statInProgress.text = d.inProgressTransfersCount.toString()
                    binding.statCompleted.text = d.completedTodayCount.toString()
                    binding.statEnergy.text = "%.1f".format(d.todayEnergyTransferredKwh)
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@OperatorHomeActivity, result.message, Toast.LENGTH_LONG).show()
                    if (result.code == 401) {
                        app.session.clear()
                        startActivity(Intent(this@OperatorHomeActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                }
            }
        }
    }

    private fun loadList() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.searchReservations(status = currentTab) }) {
                is ApiResult.Success -> {
                    adapter.submitList(result.data)
                    binding.txtEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is ApiResult.Error ->
                    Toast.makeText(this@OperatorHomeActivity, result.message, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun onReservationTap(r: ReservationDto) {
        when {
            r.status.equals("Pending", true) -> showApproveDialog(r)
            r.status.equals("Approved", true) -> showBackupCodeDialog(r)
            r.status.equals("InProgress", true) -> openFinalize(r.id)
        }
    }

    // ── Approve / reject ───────────────────────────────────────────────

    private fun showApproveDialog(r: ReservationDto) {
        AlertDialog.Builder(this)
            .setTitle(r.reservationNo)
            .setMessage("${r.prosumerNic} · ${r.tradeType} ${r.requestedKwh} kWh at ${r.nodeName}.\n\nApprove this booking?")
            .setPositiveButton("Approve") { _, _ ->
                lifecycleScope.launch {
                    when (val result = safeApi { ApiClient.api.approveReservation(r.id) }) {
                        is ApiResult.Success -> {
                            Toast.makeText(this@OperatorHomeActivity,
                                "Approved — QR pass issued to prosumer.", Toast.LENGTH_SHORT).show()
                            refresh()
                        }
                        is ApiResult.Error ->
                            Toast.makeText(this@OperatorHomeActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Reject") { _, _ ->
                lifecycleScope.launch {
                    when (val result = safeApi {
                        ApiClient.api.rejectReservation(r.id, RejectReservationRequest())
                    }) {
                        is ApiResult.Success -> {
                            Toast.makeText(this@OperatorHomeActivity,
                                "Rejected — bay released.", Toast.LENGTH_SHORT).show()
                            refresh()
                        }
                        is ApiResult.Error ->
                            Toast.makeText(this@OperatorHomeActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNeutralButton("Close", null)
            .show()
    }

    // ── Verify (QR or backup code) ─────────────────────────────────────

    private fun showBackupCodeDialog(r: ReservationDto) {
        val dialogBinding = DialogBackupCodeBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setTitle("Verify ${r.reservationNo}")
            .setMessage("Scan the QR pass from the home screen, or enter the prosumer's 6-digit backup code:")
            .setView(dialogBinding.root)
            .setPositiveButton("Verify") { _, _ ->
                val code = dialogBinding.inputCode.text?.toString()?.trim().orEmpty()
                if (code.length != 6) {
                    Toast.makeText(this, "Backup code must be 6 digits", Toast.LENGTH_SHORT).show()
                } else {
                    verify(VerifyTransferRequest(reservationId = r.id, backupCode = code))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verify(request: VerifyTransferRequest) {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.verifyTransfer(request) }) {
                is ApiResult.Success -> {
                    Toast.makeText(this@OperatorHomeActivity,
                        "Verified ${result.data.reservationNo} — transfer started.",
                        Toast.LENGTH_SHORT).show()
                    openFinalize(result.data.id)
                }
                is ApiResult.Error ->
                    AlertDialog.Builder(this@OperatorHomeActivity)
                        .setTitle("Verification failed")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
            }
        }
    }

    private fun openFinalize(reservationId: String) {
        startActivity(
            Intent(this, FinalizeTransferActivity::class.java)
                .putExtra(FinalizeTransferActivity.EXTRA_ID, reservationId)
        )
    }
}
