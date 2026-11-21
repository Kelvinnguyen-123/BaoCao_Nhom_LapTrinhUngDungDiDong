// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Int = 0,
    val userId: String,
    val taskTitle: String,
    val durationMinutes: Int,
    val themeImageUrl: String = "",
    val completedAt: Long = System.currentTimeMillis()
)
