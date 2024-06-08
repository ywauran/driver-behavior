// HistoryAdapter.kt
package com.andi.driver.behavior

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andi.driver.behavior.DetectionData
import com.andi.driver.behavior.R

class HistoryAdapter(private val listener: OnItemClickListener) : ListAdapter<DetectionData, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return HistoryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val classNameTextView: TextView = itemView.findViewById(R.id.classNameTextView)
        private val createdAtTextView: TextView = itemView.findViewById(R.id.createdAtTextView)
        private val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

        fun bind(detectionData: DetectionData) {
            classNameTextView.text = detectionData.cls
            createdAtTextView.text = detectionData.createdAt
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(position)
                }
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    class DiffCallback : DiffUtil.ItemCallback<DetectionData>() {
        override fun areItemsTheSame(oldItem: DetectionData, newItem: DetectionData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DetectionData, newItem: DetectionData): Boolean {
            return oldItem == newItem
        }
    }
}