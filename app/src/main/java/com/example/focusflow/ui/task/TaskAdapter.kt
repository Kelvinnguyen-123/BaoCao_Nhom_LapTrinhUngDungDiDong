// Thành viên phụ trách: Huy - Phân hệ Quản lý Công việc (Task Management)
package com.example.focusflow.ui.task

import android.R
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.focusflow.data.model.TaskEntity
import com.example.focusflow.databinding.ItemTaskBinding

class TaskAdapter : ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    var onItemClick: ((TaskEntity) -> Unit)? = null
    var onToggleComplete: ((TaskEntity) -> Unit)? = null
    var onDeleteClick: ((TaskEntity) -> Unit)? = null

    inner class TaskViewHolder(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: TaskEntity) {
            binding.tvTaskTitle.text = task.title

            if (task.isCompleted) {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvTaskTitle.alpha = 0.5f
                binding.tvCategory.alpha = 0.5f
                binding.tvPriority.alpha = 0.5f
            } else {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvTaskTitle.alpha = 1.0f
                binding.tvCategory.alpha = 1.0f
                binding.tvPriority.alpha = 1.0f
            }

            binding.cbCompleted.setOnCheckedChangeListener(null)
            binding.cbCompleted.isChecked = task.isCompleted
            binding.cbCompleted.setOnClickListener {
                onToggleComplete?.invoke(task)
            }

            binding.tvCategory.text = task.category
            binding.tvPriority.text = task.priority

            when (task.priority.trim()) {
                "Cao" -> {
                    binding.tvPriority.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                    binding.tvPriority.setTextColor(Color.parseColor("#C62828"))
                }
                "Trung bình" -> {
                    binding.tvPriority.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF8E1"))
                    binding.tvPriority.setTextColor(Color.parseColor("#E65100"))
                }
                "Thấp" -> {
                    binding.tvPriority.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                    binding.tvPriority.setTextColor(Color.parseColor("#2E7D32"))
                }
                else -> {
                    binding.tvPriority.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0F4F8"))
                    binding.tvPriority.setTextColor(Color.parseColor("#37474F"))
                }
            }

            val coverUrl = if (task.coverImageUrl.isBlank()) {
                "https://picsum.photos/seed/focusflow/600/300"
            } else {
                task.coverImageUrl
            }

            Glide.with(binding.ivTaskCover.context)
                .load(coverUrl)
                .placeholder(R.drawable.ic_menu_gallery)
                .error(R.drawable.ic_dialog_alert)
                .transition(DrawableTransitionOptions.withCrossFade())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.ivTaskCover)

            binding.root.setOnClickListener {
                onItemClick?.invoke(task)
            }

            binding.btnDeleteTask.setOnClickListener {
                onDeleteClick?.invoke(task)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: TaskViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.binding.ivTaskCover)
    }

    private class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem.taskId == newItem.taskId
        }

        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem == newItem
        }
    }
}
