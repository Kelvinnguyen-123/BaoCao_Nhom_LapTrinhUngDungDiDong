// Thành viên phụ trách: Thiên - Phân hệ Đồng hồ Tập trung (Pomodoro Timer)
package com.example.focusflow.ui.timer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.focusflow.data.model.FocusSessionEntity
import com.example.focusflow.databinding.ItemFocusSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionAdapter : ListAdapter<FocusSessionEntity, SessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    var onDeleteClick: ((FocusSessionEntity) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemFocusSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(
        private val binding: ItemFocusSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: FocusSessionEntity) {
            binding.tvSessionTitle.text = session.taskTitle.ifBlank { "Phiên học tập trung" }
            binding.tvDurationMinutes.text = "${session.durationMinutes} phút"

            val dateFormat = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())
            binding.tvCompletedAt.text = dateFormat.format(Date(session.completedAt))

            binding.btnDeleteSession.setOnClickListener {
                onDeleteClick?.invoke(session)
            }
        }
    }

    class SessionDiffCallback : DiffUtil.ItemCallback<FocusSessionEntity>() {
        override fun areItemsTheSame(oldItem: FocusSessionEntity, newItem: FocusSessionEntity): Boolean {
            return oldItem.sessionId == newItem.sessionId && oldItem.completedAt == newItem.completedAt
        }

        override fun areContentsTheSame(oldItem: FocusSessionEntity, newItem: FocusSessionEntity): Boolean {
            return oldItem == newItem
        }
    }
}
