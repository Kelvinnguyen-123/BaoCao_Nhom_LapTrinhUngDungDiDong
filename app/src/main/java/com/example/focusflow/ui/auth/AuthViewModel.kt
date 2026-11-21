// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusflow.data.local.FocusFlowDatabase
import com.example.focusflow.data.model.UserProfileEntity
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val userProfileDao = FocusFlowDatabase.getDatabase(application).userProfileDao()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val profileFlows = mutableMapOf<String, StateFlow<UserProfileEntity?>>()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                val result = auth.signInWithEmailAndPassword(email, password).awaitTask()
                val user = result.user
                    ?: throw IllegalStateException("Không thể lấy thông tin người dùng sau khi đăng nhập.")

                val userDoc = firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .get()
                    .awaitTask()

                val profile = if (userDoc.exists()) {
                    UserProfileEntity(
                        uid = user.uid,
                        email = userDoc.getString("email") ?: user.email ?: email,
                        displayName = userDoc.getString("displayName")
                            ?: user.displayName
                            ?: email.substringBefore("@"),
                        avatarUrl = userDoc.getString("avatarUrl")
                            ?: defaultAvatarUrl(user.email ?: email),
                        dailyGoalMinutes = (userDoc.getLong("dailyGoalMinutes") ?: 120L).toInt(),
                        totalFocusMinutes = (userDoc.getLong("totalFocusMinutes") ?: 0L).toInt()
                    )
                } else {
                    val userEmail = user.email ?: email
                    val defaultProfile = UserProfileEntity(
                        uid = user.uid,
                        email = userEmail,
                        displayName = user.displayName?.takeIf { it.isNotBlank() }
                            ?: userEmail.substringBefore("@"),
                        avatarUrl = defaultAvatarUrl(userEmail),
                        dailyGoalMinutes = 120,
                        totalFocusMinutes = 0
                    )
                    saveProfileToFirestore(defaultProfile)
                    defaultProfile
                }

                userProfileDao.insertOrUpdateUser(profile)
                _currentUser.value = user
            } catch (e: Exception) {
                _errorMessage.value = mapFirebaseException(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(displayName: String, email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).awaitTask()
                val user = result.user
                    ?: throw IllegalStateException("Không thể tạo tài khoản người dùng.")

                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdate).awaitTask()

                val avatarUrl = defaultAvatarUrl(email)
                val profile = UserProfileEntity(
                    uid = user.uid,
                    email = email,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    dailyGoalMinutes = 120,
                    totalFocusMinutes = 0
                )

                saveProfileToFirestore(profile)
                userProfileDao.insertOrUpdateUser(profile)
                _currentUser.value = user
            } catch (e: Exception) {
                _errorMessage.value = mapFirebaseException(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                auth.sendPasswordResetEmail(email).awaitTask()
                _successMessage.value =
                    "Đã gửi liên kết đặt lại mật khẩu tới $email. Vui lòng kiểm tra hộp thư."
            } catch (e: Exception) {
                _errorMessage.value = mapFirebaseException(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getUserProfileFlow(uid: String): StateFlow<UserProfileEntity?> {
        return profileFlows.getOrPut(uid) {
            userProfileDao.getProfile(uid).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = null
            )
        }
    }

    fun syncProfileIfNeeded(uid: String, fallbackEmail: String, fallbackName: String) {
        viewModelScope.launch {
            try {
                val localProfile = userProfileDao.getProfileOnce(uid)
                val userDoc = firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .get()
                    .awaitTask()

                if (userDoc.exists()) {
                    val remoteProfile = UserProfileEntity(
                        uid = uid,
                        email = userDoc.getString("email") ?: fallbackEmail,
                        displayName = userDoc.getString("displayName")
                            ?: fallbackName.ifBlank { fallbackEmail.substringBefore("@") },
                        avatarUrl = userDoc.getString("avatarUrl")
                            ?: defaultAvatarUrl(fallbackEmail),
                        dailyGoalMinutes = (userDoc.getLong("dailyGoalMinutes") ?: 120L).toInt(),
                        totalFocusMinutes = (userDoc.getLong("totalFocusMinutes") ?: 0L).toInt()
                    )
                    userProfileDao.insertOrUpdateUser(remoteProfile)
                } else if (localProfile == null) {
                    val defaultProfile = UserProfileEntity(
                        uid = uid,
                        email = fallbackEmail,
                        displayName = fallbackName.ifBlank { fallbackEmail.substringBefore("@") },
                        avatarUrl = defaultAvatarUrl(fallbackEmail),
                        dailyGoalMinutes = 120,
                        totalFocusMinutes = 0
                    )
                    saveProfileToFirestore(defaultProfile)
                    userProfileDao.insertOrUpdateUser(defaultProfile)
                }
            } catch (_: Exception) {
                // Keep cached Room data when offline during profile refresh
            }
        }
    }

    fun updateDailyGoal(uid: String, newGoalMinutes: Int) {
        if (newGoalMinutes <= 0) {
            _errorMessage.value = "Mục tiêu mỗi ngày phải lớn hơn 0 phút."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                userProfileDao.updateDailyGoal(uid, newGoalMinutes)
                firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(mapOf("dailyGoalMinutes" to newGoalMinutes), SetOptions.merge())
                    .awaitTask()
                _successMessage.value = "Đã cập nhật mục tiêu mỗi ngày: $newGoalMinutes phút."
            } catch (e: Exception) {
                _errorMessage.value = mapFirebaseException(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            userProfileDao.clearProfiles()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    private suspend fun saveProfileToFirestore(profile: UserProfileEntity) {
        val userMap = hashMapOf(
            "uid" to profile.uid,
            "email" to profile.email,
            "displayName" to profile.displayName,
            "avatarUrl" to profile.avatarUrl,
            "dailyGoalMinutes" to profile.dailyGoalMinutes,
            "totalFocusMinutes" to profile.totalFocusMinutes
        )
        firestore.collection(USERS_COLLECTION)
            .document(profile.uid)
            .set(userMap, SetOptions.merge())
            .awaitTask()
    }

    private fun defaultAvatarUrl(email: String): String {
        return "https://i.pravatar.cc/200?u=$email"
    }

    private fun mapFirebaseException(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthWeakPasswordException ->
                "Mật khẩu quá yếu. Vui lòng nhập ít nhất 6 ký tự."
            is FirebaseAuthInvalidCredentialsException ->
                "Email hoặc mật khẩu không hợp lệ. Vui lòng kiểm tra lại."
            is FirebaseAuthUserCollisionException ->
                "Email này đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác."
            is FirebaseAuthInvalidUserException ->
                "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa."
            is FirebaseNetworkException ->
                "Không có kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
            else ->
                exception.localizedMessage ?: "Đã xảy ra lỗi không xác định. Vui lòng thử lại."
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
        }
    }

    companion object {
        private const val USERS_COLLECTION = "users"
    }
}
