// Thành viên phụ trách: Huy - Phân hệ Quản lý Công việc (Task Management)
package com.example.focusflow.ui.task

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.focusflow.R
import com.example.focusflow.data.model.TaskEntity
import com.example.focusflow.databinding.ActivityTaskBinding
import com.example.focusflow.databinding.DialogTaskFormBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskBinding
    private lateinit var viewModel: TaskViewModel
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val factory = TaskViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[TaskViewModel::class.java]

        setupToolbar()
        setupSearchAndFilter()
        setupRecyclerView()
        observeViewModel()

        binding.fabAddTask.setOnClickListener {
            showTaskFormDialog(null)
        }

        viewModel.syncFromFirestore()
    }

    private fun setupToolbar() {
        binding.toolbarTask.setNavigationOnClickListener {
            finish()
        }

        binding.toolbarTask.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.btnSyncTasks) {
                viewModel.syncFromFirestore()
                true
            } else {
                false
            }
        }
    }

    private fun setupSearchAndFilter() {
        binding.etSearchTask.doOnTextChanged { text, _, _, _ ->
            viewModel.searchQuery.value = text?.toString() ?: ""
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filterText = when (checkedIds.firstOrNull()) {
                binding.chipPending.id -> "Chưa xong"
                binding.chipDone.id -> "Hoàn thành"
                else -> "Tất cả"
            }
            viewModel.selectedFilter.value = filterText
        }
    }

    private fun setupRecyclerView() {
        adapter = TaskAdapter().apply {
            onItemClick = { task ->
                showTaskFormDialog(task)
            }
            onToggleComplete = { task ->
                viewModel.toggleTaskCompleted(task)
            }
            onDeleteClick = { task ->
                showDeleteConfirmationDialog(task)
            }
        }

        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredTasks.collect { tasks ->
                        adapter.submitList(tasks)
                        binding.llEmptyState.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
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
                            Toast.makeText(this@TaskActivity, message, Toast.LENGTH_SHORT).show()
                            viewModel.clearStatusMessage()
                        }
                    }
                }
            }
        }
    }

    private fun showTaskFormDialog(existingTask: TaskEntity?) {
        val dialogBinding = DialogTaskFormBinding.inflate(layoutInflater)

        if (existingTask != null) {
            dialogBinding.tvDialogTitle.text = "Chỉnh sửa công việc"
            dialogBinding.etTaskTitle.setText(existingTask.title)
            dialogBinding.etCoverUrl.setText(existingTask.coverImageUrl)

            when (existingTask.category) {
                "Đồ án" -> dialogBinding.chipCatProject.isChecked = true
                "Cá nhân" -> dialogBinding.chipCatPersonal.isChecked = true
                "Luyện thi" -> dialogBinding.chipCatExam.isChecked = true
                else -> dialogBinding.chipCatLearning.isChecked = true
            }

            when (existingTask.priority) {
                "Cao" -> dialogBinding.chipPriorityHigh.isChecked = true
                "Thấp" -> dialogBinding.chipPriorityLow.isChecked = true
                else -> dialogBinding.chipPriorityMedium.isChecked = true
            }
        } else {
            dialogBinding.tvDialogTitle.text = "Thêm công việc mới"
            dialogBinding.chipCatLearning.isChecked = true
            dialogBinding.chipPriorityMedium.isChecked = true
        }

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(if (existingTask == null) "Thêm" else "Cập nhật", null)
            .setNegativeButton("Hủy", null)
            .create()

        alertDialog.setOnShowListener {
            val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val title = dialogBinding.etTaskTitle.text?.toString()?.trim() ?: ""

                if (title.isBlank()) {
                    dialogBinding.tilTaskTitle.error = "Vui lòng nhập tên công việc!"
                    return@setOnClickListener
                } else {
                    dialogBinding.tilTaskTitle.error = null
                }

                val category = when (dialogBinding.chipGroupCategory.checkedChipId) {
                    dialogBinding.chipCatProject.id -> "Đồ án"
                    dialogBinding.chipCatPersonal.id -> "Cá nhân"
                    dialogBinding.chipCatExam.id -> "Luyện thi"
                    else -> "Học tập"
                }

                val priority = when (dialogBinding.chipGroupPriority.checkedChipId) {
                    dialogBinding.chipPriorityHigh.id -> "Cao"
                    dialogBinding.chipPriorityLow.id -> "Thấp"
                    else -> "Trung bình"
                }

                val coverUrl = dialogBinding.etCoverUrl.text?.toString()?.trim() ?: ""

                if (existingTask == null) {
                    viewModel.addTask(title, category, priority, coverUrl)
                } else {
                    val updatedTask = existingTask.copy(
                        title = title,
                        category = category,
                        priority = priority,
                        coverImageUrl = coverUrl
                    )
                    viewModel.updateTask(updatedTask)
                }

                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    private fun showDeleteConfirmationDialog(task: TaskEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa công việc \"${task.title}\" không?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteTask(task)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
