// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.focusflow.data.model.FocusSessionEntity
import com.example.focusflow.data.model.TaskEntity
import com.example.focusflow.data.model.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(userProfile: UserProfileEntity)

    @Update
    suspend fun updateUser(userProfile: UserProfileEntity)

    @Delete
    suspend fun deleteUser(userProfile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    fun getProfile(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    suspend fun getProfileOnce(uid: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles")
    fun getAllUserProfiles(): Flow<List<UserProfileEntity>>

    @Query("UPDATE user_profiles SET dailyGoalMinutes = :newGoalMinutes WHERE uid = :uid")
    suspend fun updateDailyGoal(uid: String, newGoalMinutes: Int)

    @Query("DELETE FROM user_profiles")
    suspend fun clearProfiles()
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY createdAt DESC")
    fun getTasksByUser(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE taskId = :taskId LIMIT 1")
    fun getTaskById(taskId: Int): Flow<TaskEntity?>

    @Query("DELETE FROM tasks WHERE userId = :userId")
    suspend fun deleteAllTasksForUser(userId: String)
}

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity)

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Delete
    suspend fun deleteSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE userId = :userId ORDER BY completedAt DESC")
    fun getSessionsByUser(userId: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions WHERE userId = :userId")
    fun getTotalFocusMinutes(userId: String): Flow<Int>
}
