package android.kimyona.jammer.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.kimyona.jammer.R
import android.kimyona.jammer.core.media.AlbumArtLoader
import android.kimyona.jammer.data.entity.Track

/**
 * QueueAdapter — BULLETPROOF EDITION.
 *
 * Correções:
 * - bindingAdapterPosition verificado contra NO_POSITION
 * - try/catch em bind()
 * - Glide clear seguro
 */
class QueueAdapter(
    private val onItemClick: (Track, Int) -> Unit,
    private val onItemRemove: (Int) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<Track, QueueAdapter.QueueViewHolder>(QueueDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view, onItemClick, onItemRemove, onDragStart)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onViewRecycled(holder: QueueViewHolder) {
        super.onViewRecycled(holder)
        try {
            AlbumArtLoader.clear(holder.coverView)
        } catch (e: Exception) {
            // Ignora
        }
    }

    class QueueViewHolder(
        itemView: View,
        private val onItemClick: (Track, Int) -> Unit,
        private val onItemRemove: (Int) -> Unit,
        private val onDragStart: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        val coverView: ImageView = itemView.findViewById(R.id.ivQueueCover)
        private val tvNumber: TextView = itemView.findViewById(R.id.tvQueueNumber)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvQueueTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvQueueArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvQueueDuration)
        private val btnDrag: ImageButton = itemView.findViewById(R.id.btnDragHandle)

        fun bind(track: Track, position: Int) {
            try {
                tvNumber.text = (position + 1).toString()
                tvTitle.text = track.title
                tvArtist.text = track.artistsJoined?.replace(";", ", ")
                    ?: track.artist
                    ?: "Unknown Artist"
                tvDuration.text = formatDuration(track.durationMs)

                AlbumArtLoader.loadThumbnail(
                    itemView.context,
                    track.path,
                    coverView,
                    R.drawable.album_placeholder,
                    48
                )

                itemView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemClick(track, pos)
                    }
                }

                itemView.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemRemove(pos)
                    }
                    true
                }

                btnDrag.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onDragStart(this)
                    }
                    false
                }
            } catch (e: Exception) {
                // Evita crash em bind
            }
        }

        private fun formatDuration(ms: Long): String {
            if (ms <= 0) return "--:--"
            val seconds = ms / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            return "%d:%02d".format(minutes, secs)
        }
    }

    class QueueDiffCallback : DiffUtil.ItemCallback<Track>() {
        override fun areItemsTheSame(oldItem: Track, newItem: Track) =
            oldItem.path == newItem.path

        override fun areContentsTheSame(oldItem: Track, newItem: Track) =
            oldItem == newItem
    }
}
