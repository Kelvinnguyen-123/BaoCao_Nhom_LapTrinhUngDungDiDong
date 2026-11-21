// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.focusflow.data.model.UserProfileEntity
import com.example.focusflow.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: AuthViewModel by viewModels()
    private var currentUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            navigateToAuth()
            return
        }

        currentUid = firebaseUser.uid
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvDisplayName.text = firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Người dùng FocusFlow"
        binding.tvEmail.text = firebaseUser.email.orEmpty()

        viewModel.syncProfileIfNeeded(
            uid = currentUid,
            fallbackEmail = firebaseUser.email.orEmpty(),
            fallbackName = firebaseUser.displayName.orEmpty()
        )

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnUpdateGoal.setOnClickListener {
            binding.tilDailyGoal.error = null
            val input = binding.etDailyGoal.text?.toString()?.trim().orEmpty()
            val minutes = input.toIntOrNull()
            if (minutes == null || minutes <= 0) {
                binding.tilDailyGoal.error = "Vui lòng nhập số phút hợp lệ (> 0)"
                return@setOnClickListener
            }
            viewModel.updateDailyGoal(currentUid, minutes)
        }

        binding.btnGoToTasks.setOnClickListener {
            try {
                val taskActivityClass = Class.forName("com.example.focusflow.ui.task.TaskActivity")
                startActivity(Intent(this, taskActivityClass))
            } catch (_: ClassNotFoundException) {
                Toast.makeText(
                    this,
                    "Chức năng Task đang được tích hợp",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnGoToTimer.setOnClickListener {
            try {
                val timerActivityClass = Class.forName("com.example.focusflow.ui.timer.TimerActivity")
                startActivity(Intent(this, timerActivityClass))
            } catch (_: ClassNotFoundException) {
                Toast.makeText(
                    this,
                    "Chức năng Pomodoro đang được tích hợp",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            navigateToAuth()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.getUserProfileFlow(currentUid).collect { profile ->
                        if (profile != null) {
                            bindUserProfile(profile)
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressIndicator.visibility =
                            if (loading) View.VISIBLE else View.GONE
                        binding.btnUpdateGoal.isEnabled = !loading
                    }
                }

                launch {
                    viewModel.errorMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(this@ProfileActivity, message, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.successMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(this@ProfileActivity, message, Toast.LENGTH_SHORT).show()
                            viewModel.clearSuccessMessage()
                        }
                    }
                }
            }
        }
    }

    private fun bindUserProfile(profile: UserProfileEntity) {
        binding.tvDisplayName.text = profile.displayName.ifBlank {
            profile.email.substringBefore("@")
        }
        binding.tvEmail.text = profile.email
        binding.tvTotalMinutes.text =
            "Tổng thời gian tập trung: ${profile.totalFocusMinutes} phút"
        binding.tvDailyGoal.text =
            "Mục tiêu mỗi ngày: ${profile.dailyGoalMinutes} phút"

        if (binding.etDailyGoal.text.isNullOrBlank()) {
            binding.etDailyGoal.setText(profile.dailyGoalMinutes.toString())
        }

        Glide.with(this)
            .load(profile.avatarUrl)
            .circleCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_dialog_alert)
            .transition(DrawableTransitionOptions.withCrossFade())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivAvatar)
    }

    private fun navigateToAuth() {
        val intent = Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
