// Họ tên: Bùi Hưng Nhật Tân
// MSSV: 2474802017151
package com.example.focusflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.focusflow.data.model.FocusSessionEntity
import com.example.focusflow.data.model.TaskEntity
import com.example.focusflow.data.model.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        TaskEntity::class,
        FocusSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FocusFlowDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: FocusFlowDatabase? = null

        fun getDatabase(context: Context): FocusFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusFlowDatabase::class.java,
                    "focus_flow_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
