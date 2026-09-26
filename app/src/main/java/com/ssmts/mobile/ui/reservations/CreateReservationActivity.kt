package com.ssmts.mobile.ui.reservations

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.CreateReservationRequest
import com.ssmts.mobile.data.remote.NodeDto
import com.ssmts.mobile.data.remote.SlotDto
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityCreateReservationBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reserve an energy drop-off (Export) or charging (Import) slot.
 * BR-01: slots must start within the next 7 days.
 * BR-05: requested kWh between 0.5 and the station maximum.
 */
class CreateReservationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateReservationBinding
    private lateinit var slotAdapter: SlotAdapter

    private var nodes: List<NodeDto> = emptyList()
    private var selectedNode: NodeDto? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedSlot: SlotDto? = null

    private val isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateReservationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        slotAdapter = SlotAdapter { slot -> selectedSlot = slot }
        binding.listSlots.layoutManager = LinearLayoutManager(this)
        binding.listSlots.adapter = slotAdapter
        binding.listSlots.isNestedScrollingEnabled = false

        binding.inputDate.setText(selectedDate.format(isoDate))
        binding.inputDate.setOnClickListener { pickDate() }

        binding.btnSubmit.setOnClickListener { submit() }

        loadNodes()
    }

    private fun loadNodes() {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.listNodes() }) {
                is ApiResult.Success -> {
                    nodes = result.data.filter { it.status.equals("Active", true) }
                    val names = nodes.map { "${it.name} (${it.nodeCode})" }
                    binding.inputNode.setAdapter(
                        ArrayAdapter(this@CreateReservationActivity,
                            android.R.layout.simple_list_item_1, names)
                    )
                    binding.inputNode.setOnItemClickListener { _, _, position, _ ->
                        selectedNode = nodes[position]
                        binding.txtMaxKwh.text =
                            "Max ${nodes[position].maxKwhPerReservation} kWh per reservation"
                        loadSlots()
                    }

                    // Pre-select node when launched from the map screen.
                    intent.getStringExtra(EXTRA_NODE_ID)?.let { preselectedId ->
                        nodes.indexOfFirst { it.id == preselectedId }.takeIf { it >= 0 }?.let { i ->
                            selectedNode = nodes[i]
                            binding.inputNode.setText(names[i], false)
                            binding.txtMaxKwh.text =
                                "Max ${nodes[i].maxKwhPerReservation} kWh per reservation"
                            loadSlots()
                        }
                    }
                }
                is ApiResult.Error ->
                    Toast.makeText(this@CreateReservationActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pickDate() {
        val today = LocalDate.now()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedDate = LocalDate.of(y, m + 1, d)
                binding.inputDate.setText(selectedDate.format(isoDate))
                loadSlots()
            },
            selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
        ).apply {
            // BR-01: today through +7 days
            datePicker.minDate = System.currentTimeMillis()
            datePicker.maxDate = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        }.show()
    }

    private fun loadSlots() {
        val node = selectedNode ?: return
        selectedSlot = null
        binding.progressSlots.visibility = View.VISIBLE
        binding.txtNoSlots.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = safeApi {
                ApiClient.api.slotsForDay(node.id, selectedDate.format(isoDate))
            }) {
                is ApiResult.Success -> {
                    slotAdapter.submitList(result.data)
                    binding.txtNoSlots.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is ApiResult.Error -> {
                    slotAdapter.submitList(emptyList())
                    Toast.makeText(this@CreateReservationActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
            binding.progressSlots.visibility = View.GONE
        }
    }

    private fun submit() {
        val node = selectedNode
        val slot = selectedSlot
        val kwh = binding.inputKwh.text?.toString()?.toDoubleOrNull()

        if (node == null) { Toast.makeText(this, "Select a station", Toast.LENGTH_SHORT).show(); return }
        if (slot == null) { Toast.makeText(this, "Select a time slot", Toast.LENGTH_SHORT).show(); return }
        if (kwh == null || kwh < 0.5) {
            binding.layoutKwh.error = "Minimum 0.5 kWh"; return
        }
        if (kwh > node.maxKwhPerReservation) {
            binding.layoutKwh.error = "Maximum ${node.maxKwhPerReservation} kWh at this station"; return
        }
        binding.layoutKwh.error = null

        val tradeType = if (binding.toggleTrade.checkedButtonId == binding.btnImport.id)
            "Import" else "Export"

        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            when (val result = safeApi {
                ApiClient.api.createReservation(
                    CreateReservationRequest(node.id, slot.id, tradeType, kwh))
            }) {
                is ApiResult.Success -> {
                    Toast.makeText(this@CreateReservationActivity,
                        "Reservation ${result.data.reservationNo} created — pending approval.",
                        Toast.LENGTH_LONG).show()
                    finish()
                }
                is ApiResult.Error -> {
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(this@CreateReservationActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_NODE_ID = "extra_node_id"
    }
}
