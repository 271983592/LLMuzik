package com.wislife.llmuzik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

object TrackDiff : DiffUtil.ItemCallback<MusicBean>() {
    override fun areItemsTheSame(old: MusicBean, new: MusicBean): Boolean =
        old.musicName == new.musicName

    override fun areContentsTheSame(old: MusicBean, new: MusicBean): Boolean =
        old == new
}

class TrackAdapter(private val onClick: (Int) -> Unit) :
    ListAdapter<MusicBean, TrackAdapter.VH>(TrackDiff) {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_title)
        private val artist: TextView = itemView.findViewById(R.id.tv_artist)
        private val cover: ImageView = itemView.findViewById(R.id.iv_cover)
        private val more: ImageButton = itemView.findViewById(R.id.ib_more)

        fun bind(bean: MusicBean) {
            title.text = bean.musicName
            artist.text = bean.artist
            // 封面可后续用 Glide 加载：Glide.with(cover).load(bean.coverUrl).into(cover)
            itemView.setOnClickListener { onClick(bindingAdapterPosition) }
            more.setOnClickListener { /* 后续做菜单 */ }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))
}