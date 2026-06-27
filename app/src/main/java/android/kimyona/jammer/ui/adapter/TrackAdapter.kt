package android.kimyona.jammer.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.kimyona.jammer.R
import android.kimyona.jammer.core.media.AlbumArtLoader
import android.kimyona.jammer.data.entity.ContentRating
import android.kimyona.jammer.data.entity.Track
import android.kimyona.jammer.data.entity.contentRatingEnum
import android.kimyona.jammer.data.entity.releaseTypeEnum
import android.kimyona.jammer.data.entity.displayArtist

/**
 * TrackAdapter — v2
 *
 * Changes vs v1:
 *   - displayArtist uses Track.displayArtist (multi-artist aware, joins with " · ")
 *   - Content rating badge shown when rating != NONE, colour-coded per rating
 *   - Release type label shown when releaseType is set
 */
class TrackAdapter(
    private val onClick: (Track) -> Unit,
    private val onLongClick: ((Track, View) -> Unit)? = null
) : ListAdapter<Track, TrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: TrackViewHolder) {
        super.onViewRecycled(holder)
        AlbumArtLoader.clear(holder.coverView)
    }

    class TrackViewHolder(
        itemView: View,
        private val onClick: (Track) -> Unit,
        private val onLongClick: ((Track, View) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {

        val coverView: ImageView = itemView.findViewById(R.id.ivItemCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)
        private val tvRatingBadge: TextView = itemView.findViewById(R.id.tvItemContentRating)
        private val tvReleaseType: TextView = itemView.findViewById(R.id.tvItemReleaseType)

        fun bind(track: Track) {
            tvTitle.text = track.title
            tvArtist.text = track.displayArtist
            tvDuration.text = formatDuration(track.durationMs)

            bindRatingBadge(track.contentRatingEnum)
            bindReleaseType(track.releaseTypeEnum?.label)

            AlbumArtLoader.loadThumbnail(
                itemView.context,
                track.path,
                coverView,
                R.drawable.album_placeholder
            )

            itemView.setOnClickListener { onClick(track) }
            itemView.setOnLongClickListener { view ->
                onLongClick?.invoke(track, view)
                true
            }
        }

        private fun bindRatingBadge(rating: ContentRating) {
            if (rating == ContentRating.NONE) {
                tvRatingBadge.visibility = View.GONE
                return
            }
            tvRatingBadge.text = rating.badge
            tvRatingBadge.visibility = View.VISIBLE
            // Colour-code each rating so they are visually distinct at a glance.
            tvRatingBadge.setBackgroundColor(ratingColor(rating))
        }

        private fun bindReleaseType(label: String?) {
            if (label.isNullOrBlank()) {
                tvReleaseType.visibility = View.GONE
            } else {
                tvReleaseType.text = label
                tvReleaseType.visibility = View.VISIBLE
            }
        }

        private fun ratingColor(rating: ContentRating): Int = when (rating) {
            ContentRating.EXPLICIT  -> Color.parseColor("#CC2222")  // red
            ContentRating.VULGAR    -> Color.parseColor("#BB4400")  // dark orange
            ContentRating.SLURRY    -> Color.parseColor("#997700")  // amber
            ContentRating.LEWD      -> Color.parseColor("#883399")  // purple
            ContentRating.SENSITIVE -> Color.parseColor("#446699")  // steel blue
            ContentRating.NONE      -> Color.TRANSPARENT
        }

        private fun formatDuration(ms: Long): String {
            if (ms <= 0) return "--:--"
            val seconds = ms / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            return "%d:%02d".format(minutes, secs)
        }
    }

    class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
        override fun areItemsTheSame(oldItem: Track, newItem: Track) =
            oldItem.path == newItem.path

        override fun areContentsTheSame(oldItem: Track, newItem: Track) =
            oldItem == newItem
    }
}
