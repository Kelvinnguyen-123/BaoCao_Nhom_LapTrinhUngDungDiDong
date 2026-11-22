// Thành viên phụ trách: Thiên - Phân hệ Đồng hồ Tập trung (Pomodoro Timer)
package com.example.focusflow.ui.timer

import android.media.RingtoneManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.focusflow.R
import com.example.focusflow.data.model.FocusSessionEntity
import com.example.focusflow.databinding.ActivityTimerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

class TimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerBinding
    private lateinit var viewModel: TimerViewModel
    private lateinit var adapter: TimerAdapter
    private var previousRemainingSeconds: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        val factory = TimerViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[TimerViewModel::class.java]

        setupToolbar()
        setupInputsAndChips()
        setupButtons()
        setupRecyclerView()
        observeViewModel()

        viewModel.syncSessionsFromFirestore()
    }

    private fun setupToolbar() {
        binding.toolbarTimer.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupInputsAndChips() {
        binding.etSessionLabel.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSessionLabel(text?.toString() ?: "")
        }

        binding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.chip25Min.id -> viewModel.selectDuration(25)
                binding.chip45Min.id -> viewModel.selectDuration(45)
                binding.chip5Min.id -> viewModel.selectDuration(5)
                binding.chipCustomMin.id -> showCustomDurationDialog()
                else -> viewModel.selectDuration(25)
            }
        }
    }

    private fun showCustomDurationDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Nhập số phút (1 - 180)"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Thời gian tùy chỉnh")
            .setView(input)
            .setPositiveButton("Xác nhận") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes in 1..180) {
                    viewModel.selectDuration(minutes)
                } else {
                    Toast.makeText(this, "Vui lòng nhập số phút từ 1 đến 180", Toast.LENGTH_SHORT).show()
                    binding.chip25Min.isChecked = true
                }
            }
            .setNegativeButton("Hủy") { _, _ ->
                binding.chip25Min.isChecked = true
            }
            .setOnCancelListener {
                binding.chip25Min.isChecked = true
            }
            .show()
    }

    private fun setupButtons() {
        binding.btnStartPause.setOnClickListener {
            viewModel.toggleStartPause()
        }

        binding.btnReset.setOnClickListener {
            viewModel.resetTimer()
        }

        binding.btnFinishDemo.setOnClickListener {
            viewModel.completeSessionNow()
            playNotificationSound()
        }
    }

    private fun setupRecyclerView() {
        adapter = TimerAdapter().apply {
            onDeleteClick = { session ->
                showDeleteConfirmationDialog(session)
            }
        }

        binding.rvSessionHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSessionHistory.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.remainingSeconds.collect { remaining ->
                        val minutes = remaining / 60
                        val seconds = remaining % 60
                        binding.tvCountdownTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                        val totalSecs = viewModel.totalDurationSeconds.value
                        if (totalSecs > 0) {
                            val progress = ((remaining.toDouble() / totalSecs.toDouble()) * 100).toInt()
                            binding.progressTimer.progress = progress
                        }

                        if (previousRemainingSeconds == 1L && remaining == 0L) {
                            playNotificationSound()
                        }
                        previousRemainingSeconds = remaining
                    }
                }

                launch {
                    viewModel.isRunning.collect { isRunning ->
                        binding.btnStartPause.text = if (isRunning) "Tạm dừng" else "Bắt đầu"
                        binding.btnStartPause.setIconResource(
                            if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        )

                        binding.etSessionLabel.isEnabled = !isRunning
                        for (i in 0 until binding.chipGroupDuration.childCount) {
                            binding.chipGroupDuration.getChildAt(i).isEnabled = !isRunning
                        }

                        binding.tvTimerStatus.text = if (isRunning) "Đang đếm ngược..." else "Sẵn sàng tập trung"
                    }
                }

                launch {
                    viewModel.sessions.collect { sessionList ->
                        adapter.submitList(sessionList)
                        binding.tvEmptyHistory.visibility = if (sessionList.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvSessionCount.text = "${sessionList.size} phiên"
                    }
                }

                launch {
                    viewModel.totalFocusMinutes.collect { totalMinutes ->
                        binding.tvTotalMinutes.text = "$totalMinutes phút"
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBarLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.statusMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(this@TimerActivity, message, Toast.LENGTH_SHORT).show()
                            viewModel.clearStatusMessage()
                        }
                    }
                }
            }
        }
    }

    private fun playNotificationSound() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone.play()
        } catch (_: Exception) {
        }
    }

    private fun showDeleteConfirmationDialog(session: FocusSessionEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa phiên tập trung \"${session.taskTitle}\" (${session.durationMinutes} phút) không?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteSession(session)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
