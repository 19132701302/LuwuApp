package com.luwu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.PostItem

/** 首页轮播图适配器 */
class BannerAdapter(
    private val onItemClick: (PostItem) -> Unit,
) : RecyclerView.Adapter<BannerAdapter.Holder>() {

    private val items = mutableListOf<PostItem>()

    fun setItems(list: List<PostItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        private val iv: android.widget.ImageView = v.findViewById(R.id.iv_banner)
        private val tv: TextView = v.findViewById(R.id.tv_banner_title)

        fun bind(item: PostItem) {
            tv.text = item.title
            if (item.thumb.isNotBlank()) {
                com.luwu.app.util.ImageLoader.load(item.thumb, iv)
            }
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
