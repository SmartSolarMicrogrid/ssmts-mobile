package com.ssmts.mobile.ui.nodes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.NodeDto
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityNodesMapBinding
import com.ssmts.mobile.ui.reservations.CreateReservationActivity
import kotlinx.coroutines.launch

/** Nearby grid nodes on Google Maps with booking shortcut (Prosumer). */
class NodesMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNodesMapBinding
    private var map: GoogleMap? = null
    private var selectedNode: NodeDto? = null
    private val nodesById = mutableMapOf<String, NodeDto>()

    // Colombo fallback when location is unavailable/denied
    private val fallback = LatLng(6.9271, 79.8612)

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) centerOnMyLocation() else loadNodes(fallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNodesMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnBookHere.setOnClickListener {
            selectedNode?.let { node ->
                startActivity(
                    Intent(this, CreateReservationActivity::class.java)
                        .putExtra(CreateReservationActivity.EXTRA_NODE_ID, node.id)
                )
            }
        }

        val mapFragment =
            supportFragmentManager.findFragmentById(binding.mapContainer.id) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            map = googleMap
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.setOnMarkerClickListener { marker ->
                (marker.tag as? String)?.let { id -> nodesById[id]?.let(::showNodeCard) }
                false
            }
            requestLocation()
        }
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            centerOnMyLocation()
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerOnMyLocation() {
        map?.isMyLocationEnabled = true
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                val here = if (location != null) LatLng(location.latitude, location.longitude)
                else fallback
                loadNodes(here)
            }
            .addOnFailureListener { loadNodes(fallback) }
    }

    private fun loadNodes(center: LatLng) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 12f))
        binding.progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Prefer geospatial nearby search; fall back to the full active list.
            val nearby = safeApi {
                ApiClient.api.nearbyNodes(center.latitude, center.longitude)
            }
            val nodes = when (nearby) {
                is ApiResult.Success ->
                    if (nearby.data.isNotEmpty()) nearby.data
                    else (safeApi { ApiClient.api.listNodes() } as? ApiResult.Success)?.data.orEmpty()
                is ApiResult.Error ->
                    (safeApi { ApiClient.api.listNodes() } as? ApiResult.Success)?.data.orEmpty()
            }

            binding.progress.visibility = View.GONE
            if (nodes.isEmpty()) {
                Toast.makeText(this@NodesMapActivity,
                    "No grid stations found nearby.", Toast.LENGTH_LONG).show()
                return@launch
            }

            nodesById.clear()
            nodes.forEach { node ->
                nodesById[node.id] = node
                val marker = map?.addMarker(
                    MarkerOptions()
                        .position(LatLng(node.latitude, node.longitude))
                        .title(node.name)
                        .snippet("Sell Rs.${node.sellPricePerKwh}/kWh · Buy Rs.${node.buyPricePerKwh}/kWh")
                )
                marker?.tag = node.id
            }
            binding.txtCount.text = "${nodes.size} station(s) found"
        }
    }

    private fun showNodeCard(node: NodeDto) {
        selectedNode = node
        binding.cardNode.visibility = View.VISIBLE
        binding.txtNodeName.text = node.name
        binding.txtNodeCode.text = node.nodeCode
        binding.txtPrices.text =
            "Sell to grid: Rs. ${node.sellPricePerKwh}/kWh   ·   Buy: Rs. ${node.buyPricePerKwh}/kWh"
        binding.txtHours.text =
            "Open ${node.openTime ?: "--"} – ${node.closeTime ?: "--"} · ${node.capacityBays} bays · max ${node.maxKwhPerReservation} kWh"
    }
}
