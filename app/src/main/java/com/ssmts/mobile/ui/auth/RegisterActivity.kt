package com.ssmts.mobile.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ssmts.mobile.SsmtsApp
import com.ssmts.mobile.data.local.LocalUser
import com.ssmts.mobile.data.local.SessionManager
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.ProsumerRegisterRequest
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

/**
 * Prosumer self-registration. NIC is the primary key both on the server
 * and in the local SQLite users table (BR-09 NIC format enforced).
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val app by lazy { SsmtsApp.from(this) }

    private val nicRegex = Regex("^([0-9]{9}[VvXx]|[0-9]{12})$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
    }

    private fun attemptRegister() {
        val nic = binding.inputNic.text?.toString()?.trim().orEmpty()
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val phone = binding.inputPhone.text?.toString()?.trim().orEmpty()
        val address = binding.inputAddress.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val confirm = binding.inputConfirm.text?.toString().orEmpty()

        listOf(binding.layoutNic, binding.layoutName, binding.layoutEmail, binding.layoutPhone,
            binding.layoutAddress, binding.layoutPassword, binding.layoutConfirm)
            .forEach { it.error = null }

        var valid = true
        if (!nicRegex.matches(nic)) {
            binding.layoutNic.error = "NIC must be 9 digits + V/X or 12 digits"; valid = false
        }
        if (name.length < 2) { binding.layoutName.error = "Full name is required"; valid = false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = "Valid email is required"; valid = false
        }
        if (phone.length < 9) { binding.layoutPhone.error = "Valid phone is required"; valid = false }
        if (address.isEmpty()) { binding.layoutAddress.error = "Address is required"; valid = false }
        if (password.length < 8 || !password.any { it.isUpperCase() } || !password.any { it.isDigit() }) {
            binding.layoutPassword.error = "Min 8 chars with an uppercase letter and a digit"; valid = false
        }
        if (confirm != password) { binding.layoutConfirm.error = "Passwords do not match"; valid = false }
        if (!valid) return

        setLoading(true)
        lifecycleScope.launch {
            val request = ProsumerRegisterRequest(nic, name, email, phone, address, password)
            when (val result = safeApi { ApiClient.api.register(request) }) {
                is ApiResult.Success -> {
                    // Cache the registered prosumer in the local SQLite users table.
                    app.db.upsertUser(
                        LocalUser(
                            nic = result.data.nic,
                            fullName = result.data.fullName,
                            email = result.data.email,
                            phone = result.data.phone,
                            address = result.data.address,
                            role = SessionManager.ROLE_PROSUMER,
                            status = result.data.status,
                            serverId = result.data.id
                        )
                    )
                    Toast.makeText(
                        this@RegisterActivity,
                        "Account created! Sign in with your email and password.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                is ApiResult.Error -> {
                    setLoading(false)
                    Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }
}
