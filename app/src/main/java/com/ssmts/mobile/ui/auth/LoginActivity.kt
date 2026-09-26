package com.ssmts.mobile.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ssmts.mobile.SsmtsApp
import com.ssmts.mobile.data.local.LocalUser
import com.ssmts.mobile.data.local.SessionManager
import com.ssmts.mobile.data.remote.ApiClient
import com.ssmts.mobile.data.remote.ApiResult
import com.ssmts.mobile.data.remote.LoginRequest
import com.ssmts.mobile.data.remote.safeApi
import com.ssmts.mobile.databinding.ActivityLoginBinding
import com.ssmts.mobile.ui.operator.OperatorHomeActivity
import com.ssmts.mobile.ui.prosumer.DashboardActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val app by lazy { SsmtsApp.from(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already signed in → skip straight to the right home screen.
        if (app.session.isLoggedIn) {
            routeByRole(app.session.role ?: "")
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.txtGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        binding.layoutEmail.error = null
        binding.layoutPassword.error = null

        if (email.isEmpty()) { binding.layoutEmail.error = "Email is required"; return }
        if (password.isEmpty()) { binding.layoutPassword.error = "Password is required"; return }

        setLoading(true)
        lifecycleScope.launch {
            when (val result = safeApi { ApiClient.api.login(LoginRequest(email, password)) }) {
                is ApiResult.Success -> onLoggedIn(result.data.role, result.data)
                is ApiResult.Error -> {
                    setLoading(false)
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun onLoggedIn(role: String, auth: com.ssmts.mobile.data.remote.AuthResponse) {
        if (!role.equals(SessionManager.ROLE_PROSUMER, true) &&
            !role.equals(SessionManager.ROLE_OPERATOR, true)
        ) {
            setLoading(false)
            Toast.makeText(this, "Backoffice accounts must use the web portal.", Toast.LENGTH_LONG).show()
            return
        }

        app.session.saveSession(auth.token, auth.userId, auth.name, auth.email, role, auth.expiresAt)

        // Resolve full profile (NIC for prosumers) and cache locally in SQLite.
        when (val me = safeApi { ApiClient.api.me() }) {
            is ApiResult.Success -> {
                val user = me.data
                val key = user.nic ?: user.id
                app.session.saveNic(key)
                app.db.upsertUser(
                    LocalUser(
                        nic = key,
                        fullName = user.name,
                        email = user.email,
                        phone = user.phone,
                        address = user.address,
                        role = role,
                        status = user.status ?: "Active",
                        serverId = user.id,
                        lastLoginAt = System.currentTimeMillis()
                    )
                )
            }
            is ApiResult.Error -> { /* profile cache is best-effort; session still valid */ }
        }

        routeByRole(role)
    }

    private fun routeByRole(role: String) {
        val target = if (role.equals(SessionManager.ROLE_OPERATOR, true)) {
            OperatorHomeActivity::class.java
        } else {
            DashboardActivity::class.java
        }
        startActivity(Intent(this, target).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }
}
