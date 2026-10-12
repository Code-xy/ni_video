package com.seagull.nivod.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.seagull.nivod.History
import com.seagull.nivod.R
import com.seagull.nivod.data.Video

class VideoAdapter(
    private val onClick: (Video) -> Unit,
    private val onLongClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private val items = ArrayList<Video>()

    fun add(list: List<Video>, append: Boolean) {
        if (!append) {
            items.clear(); items.addAll(list); notifyDataSetChanged(); return
        }
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun size() = items.size
    fun items(): List<Video> = ArrayList(items)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val badge: TextView = v.findViewById(R.id.badge)
        val score: TextView = v.findViewById(R.id.score)
        val prog: View = v.findViewById(R.id.prog)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val v = items[position]
        h.title.text = v.title

        h.sub.text = v.sub
        h.sub.visibility = if (v.sub.isBlank()) View.GONE else View.VISIBLE

        h.badge.text = v.badge
        h.badge.visibility = if (v.badge.isBlank()) View.GONE else View.VISIBLE

        h.score.text = if (v.score.isBlank()) "" else v.score
        h.score.visibility = if (v.score.isBlank()) View.GONE else View.VISIBLE

        h.thumb.load(v.cover) {
            crossfade(true)
            placeholder(R.drawable.ph_thumb)
        }

        // 看过的画一条进度线（需要时长信息，站点没给就不显示）
        val last = History.last(h.itemView.context, v.id)
        if (last != null && last.second > 3000) {
            h.prog.visibility = View.VISIBLE
        } else {
            h.prog.visibility = View.GONE
        }

        h.itemView.setOnClickListener { onClick(v) }
        h.itemView.setOnLongClickListener { onLongClick(v); true }
    }
}
