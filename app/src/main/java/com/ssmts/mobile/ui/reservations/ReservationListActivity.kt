package com.ssmts.mobile.ui.reservations

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.ReservationDto
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityReservationListBinding
import com.ssmts.mobile.util.TimeUtil
import kotlinx.coroutines.launch

/**
 * My bookings — pending, upcoming and full history with free-text search
 * over reservation number, station name and status.
 */
class ReservationListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReservationListBinding
    private lateinit var adapter: ReservationAdapter

    private var all: List<ReservationDto> = emptyList()
    private var filter: Filter = Filter.ALL
    private var query: String = ""

    private enum class Filter { ALL, PENDING, UPCOMING, HISTORY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservationListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = ReservationAdapter { reservation ->
            startActivity(
                Intent(this, ReservationDetailActivity::class.java)
                    .putExtra(ReservationDetailActivity.EXTRA_ID, reservation.id)
            )
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.chipAll.setOnClickListener { setFilter(Filter.ALL) }
        binding.chipPending.setOnClickListener { setFilter(Filter.PENDING) }
        binding.chipUpcoming.setOnClickListener { setFilter(Filter.UPCOMING) }
        binding.chipHistory.setOnClickListener { setFilter(Filter.HISTORY) }

        binding.inputSearch.doAfterTextChanged {
            query = it?.toString()?.trim().orEmpty()
            applyFilter()
        }

        binding.swipeRefresh.setOnRefreshListener { load() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun setFilter(f: Filter) {
        filter = f
        applyFilter()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.myReservations() }) {
                is ApiResult.Success -> {
                    all = result.data.sortedByDescending { it.slotStartUtc }
                    applyFilter()
                }
                is ApiResult.Error ->
                    Toast.makeText(this@ReservationListActivity, result.message, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun applyFilter() {
        val byTab = when (filter) {
            Filter.ALL -> all
            Filter.PENDING -> all.filter { it.status.equals("Pending", true) }
            Filter.UPCOMING -> all.filter {
                it.isActive && TimeUtil.isFuture(it.slotStartUtc) &&
                    (it.status.equals("Pending", true) || it.status.equals("Approved", true))
            }
            Filter.HISTORY -> all.filter {
                !TimeUtil.isFuture(it.slotStartUtc) ||
                    it.status.equals("Completed", true) ||
                    it.status.equals("Cancelled", true) ||
                    it.status.equals("Rejected", true) ||
                    it.status.equals("Expired", true) ||
                    it.status.equals("NoShow", true)
            }
        }

        val result = if (query.isEmpty()) byTab else byTab.filter {
            it.reservationNo.contains(query, true) ||
                it.nodeName.contains(query, true) ||
                it.status.contains(query, true)
        }

        adapter.submitList(result)
        binding.txtEmpty.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
    }
}
