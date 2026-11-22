// Thành viên phụ trách: Thiên - Phân hệ Đồng hồ Tập trung (Pomodoro Timer)
package com.example.focusflow.ui.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focusflow.data.local.FocusFlowDatabase
import com.example.focusflow.data.local.FocusSessionDao
import com.example.focusflow.data.local.UserProfileDao
import com.example.focusflow.data.model.FocusSessionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TimerViewModel(
    application: Application,
    private val focusSessionDao: FocusSessionDao = FocusFlowDatabase.getDatabase(application).focusSessionDao(),
    private val userProfileDao: UserProfileDao = FocusFlowDatabase.getDatabase(application).userProfileDao(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AndroidViewModel(application) {

    val userId: String = FirebaseAuth.getInstance().currentUser?.uid ?: "test_user_thien"

    private val _selectedDurationMinutes = MutableStateFlow(25)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private val _totalDurationSeconds = MutableStateFlow(25 * 60L)
    val totalDurationSeconds: StateFlow<Long> = _totalDurationSeconds.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(25 * 60L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _sessionLabel = MutableStateFlow("Phiên học tập trung")
    val sessionLabel: StateFlow<String> = _sessionLabel.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var timerJob: Job? = null

    val sessions: StateFlow<List<FocusSessionEntity>> = focusSessionDao.getSessionsByUser(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalFocusMinutes: StateFlow<Int> = focusSessionDao.getTotalFocusMinutes(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun selectDuration(minutes: Int) {
        val coerced = minutes.coerceIn(1, 180)
        pauseTimer()
        _selectedDurationMinutes.value = coerced
        val seconds = coerced * 60L
        _totalDurationSeconds.value = seconds
        _remainingSeconds.value = seconds
    }

    fun updateSessionLabel(label: String) {
        _sessionLabel.value = if (label.isBlank()) "Phiên học tập trung" else label.trim()
    }

    fun setSessionLabel(label: String) = updateSessionLabel(label)

    fun toggleStartPause() {
        if (_isRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun startTimer() {
        if (_isRunning.value) return
        if (_remainingSeconds.value <= 0L) {
            _remainingSeconds.value = _totalDurationSeconds.value
        }
        _isRunning.value = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRunning.value && _remainingSeconds.value > 0L) {
                delay(1000L)
                _remainingSeconds.value -= 1L
            }
            if (_remainingSeconds.value <= 0L && _isRunning.value) {
                _isRunning.value = false
                onTimerCompleted()
            }
        }
    }

    fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        pauseTimer()
        _remainingSeconds.value = _selectedDurationMinutes.value * 60L
    }

    fun completeSessionNow() {
        pauseTimer()
        val minutes = _selectedDurationMinutes.value
        val label = _sessionLabel.value
        saveCompletedSession(label, minutes)
        resetTimer()
        _statusMessage.value = "Đã lưu phiên tập trung!"
    }

    private fun onTimerCompleted() {
        val minutes = _selectedDurationMinutes.value
        val label = _sessionLabel.value
        _statusMessage.value = "Chúc mừng! Bạn đã hoàn thành phiên tập trung $minutes phút!"
        saveCompletedSession(label, minutes)
        resetTimer()
    }

    fun saveCompletedSession(title: String, durationMinutes: Int, themeUrl: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newSession = FocusSessionEntity(
                    userId = userId,
                    taskTitle = title.ifBlank { "Phiên học tập trung" },
                    durationMinutes = durationMinutes,
                    themeImageUrl = themeUrl,
                    completedAt = System.currentTimeMillis()
                )
                focusSessionDao.insertSession(newSession)

                val userProfile = userProfileDao.getProfileOnce(userId)
                if (userProfile != null) {
                    val updatedTotal = userProfile.totalFocusMinutes + durationMinutes
                    userProfileDao.updateUser(userProfile.copy(totalFocusMinutes = updatedTotal))
                    firestore.collection("user_profiles")
                        .document(userId)
                        .update("totalFocusMinutes", updatedTotal)
                }

                val sessionMap = hashMapOf(
                    "userId" to userId,
                    "taskTitle" to newSession.taskTitle,
                    "durationMinutes" to newSession.durationMinutes,
                    "themeImageUrl" to newSession.themeImageUrl,
                    "completedAt" to newSession.completedAt
                )
                firestore.collection("focus_sessions")
                    .add(sessionMap)

            } catch (e: Exception) {
                _statusMessage.value = "Lỗi khi lưu phiên tập trung: ${e.message}"
            }
        }
    }

    fun deleteSession(session: FocusSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                focusSessionDao.deleteSession(session)
                val snapshot = firestore.collection("focus_sessions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("completedAt", session.completedAt)
                    .get()
                    .await()
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                _statusMessage.value = "Đã xóa phiên tập trung!"
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi khi xóa phiên tập trung: ${e.message}"
            }
        }
    }

    fun syncSessionsFromFirestore() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val snapshot = firestore.collection("focus_sessions")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val firestoreSessions = snapshot.documents.mapNotNull { doc ->
                    val taskTitle = doc.getString("taskTitle") ?: doc.getString("label") ?: "Phiên học tập trung"
                    val durationMinutes = (doc.getLong("durationMinutes") ?: 25L).toInt()
                    val themeImageUrl = doc.getString("themeImageUrl") ?: ""
                    val completedAt = doc.getLong("completedAt") ?: System.currentTimeMillis()

                    FocusSessionEntity(
                        userId = userId,
                        taskTitle = taskTitle,
                        durationMinutes = durationMinutes,
                        themeImageUrl = themeImageUrl,
                        completedAt = completedAt
                    )
                }

                for (session in firestoreSessions) {
                    focusSessionDao.insertSession(session)
                }

                _statusMessage.value = "Đồng bộ từ Cloud thành công!"
            } catch (e: Exception) {
                _statusMessage.value = "Lỗi đồng bộ Cloud: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun syncFromFirestore() = syncSessionsFromFirestore()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class TimerViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
