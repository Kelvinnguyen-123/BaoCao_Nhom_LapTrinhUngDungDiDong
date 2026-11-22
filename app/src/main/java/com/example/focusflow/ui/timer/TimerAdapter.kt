// Thành viên phụ trách: Thiên - Phân hệ Đồng hồ Tập trung (Pomodoro Timer)
package com.example.focusflow.ui.timer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.focusflow.data.model.FocusSessionEntity
import com.example.focusflow.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerAdapter : ListAdapter<FocusSessionEntity, TimerAdapter.SessionViewHolder>(SessionDiffCallback()) {

    var onDeleteClick: ((FocusSessionEntity) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: SessionViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.binding.ivSessionBadge)
    }

    inner class SessionViewHolder(
        val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FocusSessionEntity) {
            binding.tvSessionLabel.text = item.taskTitle.ifBlank { "Phiên học tập trung" }
            binding.tvSessionDuration.text = "+${item.durationMinutes} phút"

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvSessionTime.text = dateFormat.format(Date(item.completedAt))

            val imageUrl = if (item.themeImageUrl.isNotBlank()) {
                item.themeImageUrl
            } else {
                "https://picsum.photos/seed/focus${item.sessionId}/200/200"
            }

            Glide.with(binding.root.context)
                .load(imageUrl)
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_today)
                .error(android.R.drawable.ic_dialog_alert)
                .into(binding.ivSessionBadge)

            binding.btnDeleteSession.setOnClickListener {
                onDeleteClick?.invoke(item)
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
