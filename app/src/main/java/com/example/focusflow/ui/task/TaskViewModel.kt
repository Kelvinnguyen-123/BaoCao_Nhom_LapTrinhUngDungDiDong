// Thành viên phụ trách: Huy - Phân hệ Quản lý Công việc (Task Management)
package com.example.focusflow.ui.task

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflow.data.local.FocusFlowDatabase
import com.example.focusflow.data.local.TaskDao
import com.example.focusflow.data.model.TaskEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class TaskViewModel(
    application: Application,
    private val taskDao: TaskDao = FocusFlowDatabase.getDatabase(application).taskDao(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AndroidViewModel(application) {

    val userId: String = FirebaseAuth.getInstance().currentUser?.uid ?: "test_user_huy"

    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow("Tất cả")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val filteredTasks: StateFlow<List<TaskEntity>> = combine(
        taskDao.getTasksByUser(userId),
        searchQuery,
        selectedFilter
    ) { tasks, query, filter ->
        tasks.filter { task ->
            val matchesQuery = query.isBlank() || task.title.contains(query.trim(), ignoreCase = true)
            val matchesFilter = when (filter) {
                "Chưa xong" -> !task.isCompleted
                "Hoàn thành" -> task.isCompleted
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addTask(title: String, category: String, priority: String, coverImageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedTitle = title.trim()
                val trimmedCategory = if (category.isBlank()) "Học tập" else category.trim()
                val trimmedPriority = if (priority.isBlank()) "Trung bình" else priority.trim()
                val finalCoverUrl = if (coverImageUrl.isBlank()) {
                    "https://picsum.photos/seed/${abs(trimmedCategory.hashCode())}/600/300"
                } else {
                    coverImageUrl.trim()
                }

                val newTask = TaskEntity(
                    userId = userId,
                    title = trimmedTitle,
                    category = trimmedCategory,
                    priority = trimmedPriority,
                    coverImageUrl = finalCoverUrl,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis()
                )

                val insertedId = taskDao.insertTask(newTask).toInt()
                val taskWithId = newTask.copy(taskId = insertedId)

                val taskMap = hashMapOf(
                    "taskId" to insertedId,
                    "userId" to userId,
                    "title" to trimmedTitle,
                    "category" to trimmedCategory,
                    "priority" to trimmedPriority,
                    "coverImageUrl" to finalCoverUrl,
                    "isCompleted" to false,
                    "createdAt" to taskWithId.createdAt
                )

                firestore.collection("tasks")
                    .document("${userId}_${insertedId}")
                    .set(taskMap)

                _statusMessage.value = "Thêm công việc thành công!"
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi khi thêm công việc: ${e.message}"
            }
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedTitle = task.title.trim()
                val trimmedCategory = if (task.category.isBlank()) "Học tập" else task.category.trim()
                val trimmedPriority = if (task.priority.isBlank()) "Trung bình" else task.priority.trim()
                val finalCoverUrl = if (task.coverImageUrl.isBlank()) {
                    "https://picsum.photos/seed/${abs(trimmedCategory.hashCode())}/600/300"
                } else {
                    task.coverImageUrl.trim()
                }

                val updatedTask = task.copy(
                    title = trimmedTitle,
                    category = trimmedCategory,
                    priority = trimmedPriority,
                    coverImageUrl = finalCoverUrl
                )

                taskDao.updateTask(updatedTask)

                val taskMap = hashMapOf(
                    "taskId" to updatedTask.taskId,
                    "userId" to updatedTask.userId,
                    "title" to updatedTask.title,
                    "category" to updatedTask.category,
                    "priority" to updatedTask.priority,
                    "coverImageUrl" to updatedTask.coverImageUrl,
                    "isCompleted" to updatedTask.isCompleted,
                    "createdAt" to updatedTask.createdAt
                )

                firestore.collection("tasks")
                    .document("${userId}_${updatedTask.taskId}")
                    .set(taskMap)

                _statusMessage.value = "Cập nhật công việc thành công!"
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi cập nhật: ${e.message}"
            }
        }
    }

    fun toggleTaskCompleted(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedTask = task.copy(isCompleted = !task.isCompleted)
                taskDao.updateTask(updatedTask)

                firestore.collection("tasks")
                    .document("${userId}_${task.taskId}")
                    .update("isCompleted", updatedTask.isCompleted)
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi cập nhật trạng thái: ${e.message}"
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskDao.deleteTask(task)

                firestore.collection("tasks")
                    .document("${userId}_${task.taskId}")
                    .delete()

                _statusMessage.value = "Đã xóa công việc!"
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi khi xóa: ${e.message}"
            }
        }
    }

    fun syncFromFirestore() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val snapshot = firestore.collection("tasks")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val firestoreTasks = snapshot.documents.mapNotNull { doc ->
                    val taskId = (doc.getLong("taskId") ?: 0L).toInt()
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val category = doc.getString("category") ?: "Học tập"
                    val priority = doc.getString("priority") ?: "Trung bình"
                    val coverImageUrl = doc.getString("coverImageUrl") ?: ""
                    val isCompleted = doc.getBoolean("isCompleted") ?: false
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                    TaskEntity(
                        taskId = taskId,
                        userId = userId,
                        title = title,
                        category = category,
                        priority = priority,
                        coverImageUrl = coverImageUrl,
                        isCompleted = isCompleted,
                        createdAt = createdAt
                    )
                }

                for (task in firestoreTasks) {
                    taskDao.insertTask(task)
                }

                _statusMessage.value = "Đồng bộ từ Cloud thành công!"
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi đồng bộ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

class TaskViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
