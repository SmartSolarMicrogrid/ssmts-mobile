package com.ssmts.mobile.ui.prosumer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ssmts.mobile.SsmtsApp
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.ProsumerUpdateRequest
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityProfileBinding
import com.ssmts.mobile.ui.auth.LoginActivity
import kotlinx.coroutines.launch

/**
 * Prosumer profile — view and edit contact details (name, phone, address)
 * and request account deactivation (blocked server-side while active
 * bookings exist — BR-10). Profile edits are mirrored into the local
 * SQLite users table.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val app by lazy { SsmtsApp.from(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDeactivate.setOnClickListener { confirmDeactivation() }

        // Instant render from the local SQLite cache, then refresh from server.
        app.session.nic?.let { nic ->
            app.db.getUser(nic)?.let { cached ->
                binding.txtNic.text = cached.nic
                binding.txtEmail.text = cached.email
                binding.inputName.setText(cached.fullName)
                binding.inputPhone.setText(cached.phone ?: "")
                binding.inputAddress.setText(cached.address ?: "")
            }
        }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.myProfile() }) {
                is ApiResult.Success -> {
                    val p = result.data
                    binding.txtNic.text = p.nic
                    binding.txtEmail.text = p.email
                    binding.txtStatus.text = p.status
                    binding.inputName.setText(p.fullName)
                    binding.inputPhone.setText(p.phone ?: "")
                    binding.inputAddress.setText(p.address ?: "")
                }
                is ApiResult.Error ->
                    Toast.makeText(this@ProfileActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val phone = binding.inputPhone.text?.toString()?.trim().orEmpty()
        val address = binding.inputAddress.text?.toString()?.trim().orEmpty()

        if (name.length < 2) {
            binding.layoutName.error = "Full name is required"; return
        }
        binding.layoutName.error = null

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            when (val result = safeApi {
                ApiClient.api.updateMyProfile(ProsumerUpdateRequest(name, phone, address))
            }) {
                is ApiResult.Success -> {
                    // Keep the SQLite user record in sync with the server.
                    app.db.updateProfile(result.data.nic, result.data.fullName,
                        result.data.phone, result.data.address)
                    Toast.makeText(this@ProfileActivity, "Profile updated.", Toast.LENGTH_SHORT).show()
                }
                is ApiResult.Error ->
                    Toast.makeText(this@ProfileActivity, result.message, Toast.LENGTH_LONG).show()
            }
            binding.btnSave.isEnabled = true
        }
    }

    private fun confirmDeactivation() {
        AlertDialog.Builder(this)
            .setTitle("Deactivate account")
            .setMessage(
                "Request deactivation of your prosumer account? " +
                    "This is blocked while you still have active bookings. " +
                    "A Backoffice administrator must reactivate you later."
            )
            .setPositiveButton("Request deactivation") { _, _ -> deactivate() }
            .setNegativeButton("Keep account", null)
            .show()
    }

    private fun deactivate() {
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.requestDeactivation() }) {
                is ApiResult.Success -> {
                    app.session.nic?.let { app.db.updateStatus(it, "Deactivated") }
                    Toast.makeText(this@ProfileActivity,
                        "Account deactivated. Signing out…", Toast.LENGTH_LONG).show()
                    app.session.clear()
                    startActivity(
                        Intent(this@ProfileActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                is ApiResult.Error ->
                    Toast.makeText(this@ProfileActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
