package android.kimyona.jammer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import android.kimyona.jammer.R
import android.kimyona.jammer.core.media.AlbumArtLoader
import android.kimyona.jammer.data.entity.Track
import android.kimyona.jammer.ui.viewmodel.PlayerViewModel

/**
 * PlayerFragment evoluído — Capa real, Shuffle, Repeat, Multi-artist.
 */
class PlayerFragment : Fragment() {

    private val viewModel: PlayerViewModel by activityViewModels()

    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton

    private var isSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivCover = view.findViewById(R.id.ivCover)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvArtist = view.findViewById(R.id.tvArtist)
        tvAlbum = view.findViewById(R.id.tvAlbum)
        seekBar = view.findViewById(R.id.seekBar)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)

        // Estado inicial vazio
        showEmptyState()

        // Observa track atual
        viewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                showTrack(track)
            } else {
                showEmptyState()
            }
        }

        // Observa estado de play/pause
        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        // Observa posição — atualiza seekbar e tempos
        viewModel.currentPosition.observe(viewLifecycleOwner) { posMs ->
            if (!isSeeking) {
                val duration = viewModel.getDuration()
                if (duration > 0) {
                    seekBar.max = duration.toInt()
                    seekBar.progress = posMs.toInt()
                    tvTotalTime.text = formatTime(duration)
                } else {
                    val trackDuration = viewModel.currentTrack.value?.durationMs ?: 0
                    if (trackDuration > 0) {
                        seekBar.max = trackDuration.toInt()
                        seekBar.progress = posMs.toInt()
                        tvTotalTime.text = formatTime(trackDuration)
                    }
                }
                tvCurrentTime.text = formatTime(posMs)
            }
        }

        // Observa shuffle
        viewModel.shuffleEnabled.observe(viewLifecycleOwner) { enabled ->
            updateShuffleUI(enabled)
        }

        // Observa repeat
        viewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            updateRepeatUI(mode)
        }

        // Controles
        btnPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        btnPrev.setOnClickListener { viewModel.skipPrevious() }
        btnNext.setOnClickListener { viewModel.skipNext() }
        btnShuffle.setOnClickListener { viewModel.toggleShuffle() }
        btnRepeat.setOnClickListener { viewModel.toggleRepeat() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                seekBar?.let { viewModel.seekTo(it.progress.toLong()) }
            }
        })
    }

    private fun showEmptyState() {
        tvTitle.text = "No track playing"
        tvArtist.text = "Select a song from Library"
        tvAlbum.text = ""
        ivCover.setImageResource(R.drawable.album_placeholder_vinyl)
        tvCurrentTime.text = "0:00"
        tvTotalTime.text = "0:00"
        seekBar.max = 100
        seekBar.progress = 0
        seekBar.isEnabled = false
        btnPlayPause.isEnabled = false
        btnPrev.isEnabled = false
        btnNext.isEnabled = false
        btnShuffle.isEnabled = false
        btnRepeat.isEnabled = false
    }

    private fun showTrack(track: Track) {
        tvTitle.text = track.title
        tvArtist.text = formatArtists(track)
        tvAlbum.text = track.album

        // Carrega capa REAL do arquivo
        AlbumArtLoader.load(
            requireContext(),
            track.path,
            ivCover,
            R.drawable.album_placeholder_vinyl
        )

        seekBar.isEnabled = true
        btnPlayPause.isEnabled = true
        btnPrev.isEnabled = true
        btnNext.isEnabled = true
        btnShuffle.isEnabled = true
        btnRepeat.isEnabled = true

        val duration = viewModel.getDuration()
        if (duration > 0) {
            seekBar.max = duration.toInt()
            tvTotalTime.text = formatTime(duration)
        } else if (track.durationMs > 0) {
            seekBar.max = track.durationMs.toInt()
            tvTotalTime.text = formatTime(track.durationMs)
        }
    }

    private fun formatArtists(track: Track): String {
        return track.artistsJoined?.replace(";", ", ")
            ?: track.artist
            ?: "Unknown Artist"
    }

    private fun updateShuffleUI(enabled: Boolean) {
        val tint = if (enabled) R.color.teal_200 else android.R.color.darker_gray
        btnShuffle.setColorFilter(ContextCompat.getColor(requireContext(), tint))
    }

    private fun updateRepeatUI(mode: PlayerViewModel.RepeatMode) {
        val (icon, tint) = when (mode) {
            PlayerViewModel.RepeatMode.NONE -> R.drawable.ic_repeat to android.R.color.darker_gray
            PlayerViewModel.RepeatMode.ALL -> R.drawable.ic_repeat to R.color.teal_200
            PlayerViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one to R.color.teal_200
        }
        btnRepeat.setImageResource(icon)
        btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), tint))
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AlbumArtLoader.clear(ivCover)
    }
}
