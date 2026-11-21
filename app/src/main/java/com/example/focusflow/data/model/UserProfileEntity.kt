// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val uid: String,
    val email: String,
    val displayName: String = "",
    val avatarUrl: String = "",
    val dailyGoalMinutes: Int = 120,
    val totalFocusMinutes: Int = 0
)
