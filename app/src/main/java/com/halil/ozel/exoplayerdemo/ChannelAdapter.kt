package com.halil.ozel.exoplayerdemo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.halil.ozel.exoplayerdemo.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onChannelClick: (M3UChannel) -> Unit
) : ListAdapter<M3UChannel, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: M3UChannel) {
            binding.tvChannelName.text = channel.name
            binding.tvChannelGroup.text = channel.group ?: "No Group"

            if (!channel.logoUrl.isNullOrEmpty()) {
                binding.ivChannelLogo.load(channel.logoUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            } else {
                binding.ivChannelLogo.setImageResource(R.drawable.ic_launcher_background)
            }

            binding.root.setOnClickListener { onChannelClick(channel) }
        }
    }

    private class ChannelDiffCallback : DiffUtil.ItemCallback<M3UChannel>() {
        override fun areItemsTheSame(oldItem: M3UChannel, newItem: M3UChannel): Boolean {
            return oldItem.streamUrl == newItem.streamUrl
        }

        override fun areContentsTheSame(oldItem: M3UChannel, newItem: M3UChannel): Boolean {
            return oldItem == newItem
        }
    }
}