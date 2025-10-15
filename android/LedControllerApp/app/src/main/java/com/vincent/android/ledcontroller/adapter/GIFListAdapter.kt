package com.vincent.android.ledcontroller.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.databinding.ItemGifBinding


class GIFListAdapter(
    private val context: Context, 
    private val gifFiles: List<String>,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ViewHolder>() {
    private var selectedPosition = -1
    private val viewHolders = mutableListOf<GIFViewHolder>()
    private var isPaused = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemGifBinding.inflate(LayoutInflater.from(context), parent, false)
        val viewHolder = GIFViewHolder(binding)
        viewHolders.add(viewHolder)
        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder as GIFViewHolder
        val gifPath = gifFiles[position]
        
        // 如果当前是暂停状态，加载静态图片；否则加载GIF
        if (isPaused) {
            Glide.with(holder.binding.ivGif)
                .asBitmap()
                .load(getPath(gifPath))
                .into(holder.binding.ivGif)
        } else {
            Glide.with(holder.binding.ivGif)
                .asGif()
                .load(getPath(gifPath))
                .into(holder.binding.ivGif)
        }
        
        // 根据选中状态设置边框
        val frameLayout = holder.binding.root as FrameLayout
        if (position == selectedPosition) {
            frameLayout.setBackgroundResource(R.drawable.gif_item_border_selected)
        } else {
            frameLayout.setBackgroundResource(R.drawable.gif_item_border_default)
        }

        holder.binding.root.setOnClickListener(View.OnClickListener { v: View? ->
            notifyItemChanged(selectedPosition)
            selectedPosition = position
            notifyItemChanged(selectedPosition)
            onSelectionChanged?.invoke(selectedPosition)
        })
    }

    private inline fun getPath(gifPath: String) :String {
        return "file:///android_asset/$gifPath"
    }

    override fun getItemCount(): Int {
        return gifFiles.size
    }

    fun getSelect(): Int {
        return selectedPosition
    }

    /**
     * 暂停所有GIF播放
     */
    fun pauseGifPlayback() {
        isPaused = true
        notifyDataSetChanged()
    }

    /**
     * 恢复所有GIF播放
     */
    fun resumeGifPlayback() {
        isPaused = false
        notifyDataSetChanged()
    }

    /**
     * 检查当前是否处于暂停状态
     */
    fun isPaused(): Boolean {
        return isPaused
    }

    open class GIFViewHolder(val binding: ItemGifBinding) : ViewHolder(binding.root)
}