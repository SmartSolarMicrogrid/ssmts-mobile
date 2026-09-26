package com.ssmts.mobile.ui.prosumer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ssmts.mobile.SsmtsApp
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityDashboardBinding
import com.ssmts.mobile.ui.auth.LoginActivity
import com.ssmts.mobile.ui.nodes.NodesMapActivity
import com.ssmts.mobile.ui.reservations.CreateReservationActivity
import com.ssmts.mobile.ui.reservations.ReservationAdapter
import com.ssmts.mobile.ui.reservations.ReservationDetailActivity
import com.ssmts.mobile.ui.reservations.ReservationListActivity
import kotlinx.coroutines.launch

/** Prosumer home — booking counts, energy stats and upcoming reservations. */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val app by lazy { SsmtsApp.from(this) }
    private lateinit var adapter: ReservationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtGreeting.text = "Hello, ${app.session.name ?: "Prosumer"}"

        adapter = ReservationAdapter { reservation ->
            startActivity(
                Intent(this, ReservationDetailActivity::class.java)
                    .putExtra(ReservationDetailActivity.EXTRA_ID, reservation.id)
            )
        }
        binding.listUpcoming.layoutManager = LinearLayoutManager(this)
        binding.listUpcoming.adapter = adapter
        binding.listUpcoming.isNestedScrollingEnabled = false

        binding.actionBook.setOnClickListener {
            startActivity(Intent(this, CreateReservationActivity::class.java))
        }
        binding.actionBookings.setOnClickListener {
            startActivity(Intent(this, ReservationListActivity::class.java))
        }
        binding.actionMap.setOnClickListener {
            startActivity(Intent(this, NodesMapActivity::class.java))
        }
        binding.actionProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.btnLogout.setOnClickListener { confirmLogout() }

        binding.swipeRefresh.setOnRefreshListener { loadDashboard() }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.prosumerDashboard() }) {
                is ApiResult.Success -> {
                    val d = result.data
                    binding.statActive.text = d.activeBookingsCount.toString()
                    binding.statCompleted.text = d.completedTransfersCount.toString()
                    binding.statExported.text = "%.1f".format(d.totalEnergyExportedKwh)
                    binding.statImported.text = "%.1f".format(d.totalEnergyImportedKwh)
                    binding.txtEarnings.text = "Rs. %,.2f".format(d.netEarnings)

                    adapter.submitList(d.upcomingBookings)
                    binding.txtEmptyUpcoming.visibility =
                        if (d.upcomingBookings.isEmpty()) View.VISIBLE else View.GONE
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@DashboardActivity, result.message, Toast.LENGTH_LONG).show()
                    if (result.code == 401) logout()
                }
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sign out")
            .setMessage("Sign out of your SSMTS account?")
            .setPositiveButton("Sign out") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        app.session.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
