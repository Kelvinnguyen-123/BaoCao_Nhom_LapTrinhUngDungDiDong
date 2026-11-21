// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.focusflow.R
import com.example.focusflow.databinding.ActivityAuthBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login check before inflating UI
        if (FirebaseAuth.getInstance().currentUser != null) {
            navigateToProfile()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupModeToggle()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupModeToggle() {
        updateModeUi(registerMode = false)

        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val registerMode = checkedId == R.id.btnModeRegister
            updateModeUi(registerMode)
        }
    }

    private fun updateModeUi(registerMode: Boolean) {
        isRegisterMode = registerMode
        clearFieldErrors()

        if (registerMode) {
            binding.tilDisplayName.visibility = View.VISIBLE
            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.tvForgotPassword.visibility = View.GONE
            binding.btnSubmit.text = "Đăng ký"
        } else {
            binding.tilDisplayName.visibility = View.GONE
            binding.tilConfirmPassword.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.VISIBLE
            binding.btnSubmit.text = "Đăng nhập"
        }
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener {
            submitForm()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun submitForm() {
        clearFieldErrors()

        val displayName = binding.etDisplayName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        var isValid = true

        if (isRegisterMode && displayName.isEmpty()) {
            binding.tilDisplayName.error = "Vui lòng nhập họ và tên"
            isValid = false
        }

        if (email.isEmpty()) {
            binding.tilEmail.error = "Vui lòng nhập email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Email không đúng định dạng"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Vui lòng nhập mật khẩu"
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Mật khẩu phải có ít nhất 6 ký tự"
            isValid = false
        }

        if (isRegisterMode) {
            if (confirmPassword.isEmpty()) {
                binding.tilConfirmPassword.error = "Vui lòng xác nhận mật khẩu"
                isValid = false
            } else if (confirmPassword != password) {
                binding.tilConfirmPassword.error = "Mật khẩu xác nhận không khớp"
                isValid = false
            }
        }

        if (!isValid) return

        if (isRegisterMode) {
            viewModel.register(displayName, email, password)
        } else {
            viewModel.login(email, password)
        }
    }

    private fun showForgotPasswordDialog() {
        val currentEmail = binding.etEmail.text?.toString()?.trim().orEmpty()

        val inputLayout = TextInputLayout(this).apply {
            hint = "Nhập email đã đăng ký"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 24, 48, 8)
        }
        val emailInput = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(currentEmail)
        }
        inputLayout.addView(emailInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Khôi phục mật khẩu")
            .setMessage("Hệ thống sẽ gửi liên kết đặt lại mật khẩu qua email của bạn.")
            .setView(inputLayout)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Gửi yêu cầu") { _, _ ->
                val resetEmail = emailInput.text?.toString()?.trim().orEmpty()
                if (resetEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(resetEmail).matches()) {
                    Toast.makeText(
                        this,
                        "Vui lòng nhập địa chỉ email hợp lệ",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    viewModel.sendPasswordReset(resetEmail)
                }
            }
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressIndicator.visibility =
                            if (loading) View.VISIBLE else View.GONE
                        binding.btnSubmit.isEnabled = !loading
                        binding.toggleGroupMode.isEnabled = !loading
                        binding.tvForgotPassword.isEnabled = !loading
                    }
                }

                launch {
                    viewModel.errorMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(this@AuthActivity, message, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.successMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(this@AuthActivity, message, Toast.LENGTH_LONG).show()
                            viewModel.clearSuccessMessage()
                        }
                    }
                }

                launch {
                    viewModel.currentUser.collect { user ->
                        if (user != null) {
                            navigateToProfile()
                        }
                    }
                }
            }
        }
    }

    private fun clearFieldErrors() {
        binding.tilDisplayName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun navigateToProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
        finish()
    }
}
