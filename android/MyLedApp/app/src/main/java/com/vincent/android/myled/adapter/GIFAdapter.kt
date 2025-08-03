package com.vincent.android.myled.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.vincent.android.myled.R


class GIFAdapter(private val mContext: Context, private val mImages: Array<Int>) :
    RecyclerView.Adapter<ViewHolder>() {
    private var selectedPosition = -1 // 当前选中的位置

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view: View = LayoutInflater.from(mContext).inflate(R.layout.item_gif, parent, false)
        return GIFViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder as GIFViewHolder
        Glide.with(holder.imageView)
            .asGif()
            .load(mImages[position])
            .into(holder.imageView)
        holder.overlay.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
            notifyItemChanged(selectedPosition) // 更新之前选中的项
            selectedPosition = position // 更新选中的位置
            notifyItemChanged(selectedPosition) // 更新当前选中的项
        })
    }

    override fun getItemCount(): Int {
        return mImages.size
    }

    fun getSelect(): Int {
        return selectedPosition
    }

    open class GIFViewHolder(itemView: View) : ViewHolder(itemView) {
        var imageView: ImageView = itemView.findViewById<ImageView>(R.id.iv_gif)
        var overlay: View = itemView.findViewById<View>(R.id.overlay)
    }
}