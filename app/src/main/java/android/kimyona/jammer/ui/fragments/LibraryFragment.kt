package android.kimyona.jammer.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.kimyona.jammer.R
import android.kimyona.jammer.data.JammerDatabase
import android.kimyona.jammer.data.entity.ContentRating
import android.kimyona.jammer.data.entity.Playlist
import android.kimyona.jammer.data.entity.PlaylistTrackCrossRef
import android.kimyona.jammer.data.entity.ReleaseType
import android.kimyona.jammer.data.entity.Track
import android.kimyona.jammer.ui.adapter.TrackAdapter
import android.kimyona.jammer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {

    private val viewModel: PlayerViewModel by activityViewModels()
    private lateinit var adapter: TrackAdapter
    private lateinit var tvScanStatus: TextView
    private var hasTriggeredScan = false

    private var activeRatingFilter: ContentRating? = null
    private var activeTypeFilter: ReleaseType? = null
    private var currentSearchQuery: String = ""

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context?.contentResolver?.takePersistableUriPermission(it, takeFlags)

            val prefs = requireContext().getSharedPreferences(
                "jammer_prefs", android.content.Context.MODE_PRIVATE
            )
            val folders = prefs.getStringSet("saf_folders", mutableSetOf())
                ?.toMutableSet() ?: mutableSetOf()
            folders.add(it.toString())
            prefs.edit().putStringSet("saf_folders", folders).apply()

            tvScanStatus.text = "Scanning added folder..."
            viewModel.scanSAF(it)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tvScanStatus.text = "Permission granted! Scanning..."
            viewModel.scanLibrary()
        } else {
            tvScanStatus.text = "Permission DENIED. Tap FAB to retry."
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerLibrary)
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        val chipGroupRating = view.findViewById<ChipGroup>(R.id.chipGroupRating)
        val chipGroupType = view.findViewById<ChipGroup>(R.id.chipGroupReleaseType)
        tvScanStatus = view.findViewById(R.id.tvScanStatus)
        val fabRescan = view.findViewById<FloatingActionButton>(R.id.fabRescan)
        val btnAddFolder = view.findViewById<Button>(R.id.btnAddFolder)

        adapter = TrackAdapter(
            onClick = { track -> viewModel.playTrack(track) },
            onLongClick = { track, anchor -> showTrackContextMenu(track, anchor) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        setupFilterChips(chipGroupRating, chipGroupType)
        setupSearch(searchView)
        setupObservers()

        fabRescan.setOnClickListener { checkPermissionAndScan() }
        btnAddFolder.setOnClickListener { folderPickerLauncher.launch(null) }

        autoScanIfNeeded()
        restoreSafFolders()
    }

    private fun setupFilterChips(ratingGroup: ChipGroup, typeGroup: ChipGroup) {
        ratingGroup.addView(buildChip("All", isChecked = true) {
            activeRatingFilter = null
            applyFilter()
        })
        ContentRating.flagged.forEach { rating ->
            ratingGroup.addView(buildChip(rating.label) {
                activeRatingFilter = rating
                applyFilter()
            })
        }

        typeGroup.addView(buildChip("All", isChecked = true) {
            activeTypeFilter = null
            applyFilter()
        })
        ReleaseType.entries.forEach { type ->
            typeGroup.addView(buildChip(type.label) {
                activeTypeFilter = type
                applyFilter()
            })
        }
    }

    private fun buildChip(label: String, isChecked: Boolean = false, onClick: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCheckable = true
            this.isChecked = isChecked
            setOnClickListener { onClick() }
        }
    }

    private fun setupSearch(searchView: SearchView) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(query: String?): Boolean {
                currentSearchQuery = query.orEmpty()
                applyFilter()
                return true
            }
        })
    }

    private fun applyFilter() {
        if (currentSearchQuery.isNotBlank()) {
            viewModel.searchWithFilter(
                query = currentSearchQuery,
                contentRating = activeRatingFilter,
                releaseType = activeTypeFilter
            ).observe(viewLifecycleOwner) { tracks ->
                adapter.submitList(tracks)
                tvScanStatus.text = "${tracks?.size ?: 0} results"
            }
        } else {
            when {
                activeRatingFilter != null ->
                    viewModel.filterByContentRating(activeRatingFilter!!)
                        .observe(viewLifecycleOwner) { tracks ->
                            adapter.submitList(tracks)
                            tvScanStatus.text = "${tracks?.size ?: 0} tracks - ${activeRatingFilter!!.label}"
                        }
                activeTypeFilter != null ->
                    viewModel.filterByReleaseType(activeTypeFilter!!)
                        .observe(viewLifecycleOwner) { tracks ->
                            adapter.submitList(tracks)
                            tvScanStatus.text = "${tracks?.size ?: 0} tracks - ${activeTypeFilter!!.label}"
                        }
                else ->
                    viewModel.allTracks.observe(viewLifecycleOwner) { tracks ->
                        adapter.submitList(tracks)
                        if (tracks.isNullOrEmpty()) {
                            tvScanStatus.text = "No tracks found.\nTap '+' to pick a music folder."
                        } else {
                            tvScanStatus.text = "${tracks.size} tracks"
                        }
                    }
            }
        }
    }

    private fun setupObservers() {
        viewModel.allTracks.observe(viewLifecycleOwner) { tracks ->
            if (currentSearchQuery.isBlank() && activeRatingFilter == null && activeTypeFilter == null) {
                adapter.submitList(tracks)
                tvScanStatus.text = if (tracks.isNullOrEmpty()) {
                    "No tracks found.\nTap '+' to pick a music folder."
                } else {
                    "${tracks.size} tracks"
                }
            }
        }

        viewModel.scanProgress.observe(viewLifecycleOwner) { progress ->
            progress?.let { tvScanStatus.text = it }
        }
    }

    private fun showTrackContextMenu(track: Track, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.track_context_menu, popup.menu)
        popup.menu.add(0, R.id.action_edit_metadata, 99, "Edit metadata...")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play -> { viewModel.playTrack(track); true }
                R.id.action_add_queue -> {
                    viewModel.addToQueue(track)
                    Toast.makeText(requireContext(), "Added to queue", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_favorite -> {
                    viewModel.toggleFavorite(track)
                    Toast.makeText(requireContext(), "Toggled favourite", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_add_playlist -> { showAddToPlaylistDialog(track); true }
                R.id.action_edit_metadata -> { showEditMetadataDialog(track); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditMetadataDialog(track: Track) {
        val aliasInput = EditText(requireContext()).apply {
            hint = "Alias (romanized / alternate name)"
            setText(track.alias ?: "")
        }

        val ratingLabels = (listOf("- None -") + ContentRating.flagged.map { it.label }).toTypedArray()
        var selectedRatingIndex = ContentRating.flagged.indexOfFirst {
            it.name == track.contentRating
        }.let { if (it < 0) 0 else it + 1 }

        val typeLabels = (listOf("- Unknown -") + ReleaseType.entries.map { it.label }).toTypedArray()
        var selectedTypeIndex = ReleaseType.entries.indexOfFirst {
            it.name == track.releaseType
        }.let { if (it < 0) 0 else it + 1 }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit metadata - ${track.title}")
            .setView(aliasInput)
            .setPositiveButton("Save") { _, _ ->
                val alias = aliasInput.text.toString().trim().ifEmpty { null }
                viewModel.setAlias(track.path, alias)

                val rating = if (selectedRatingIndex == 0) ContentRating.NONE
                             else ContentRating.flagged[selectedRatingIndex - 1]
                viewModel.setContentRating(track.path, rating)

                val type = if (selectedTypeIndex == 0) null
                           else ReleaseType.entries[selectedTypeIndex - 1]
                viewModel.setReleaseType(track.path, type)

                Toast.makeText(requireContext(), "Metadata saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Rating...") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Content rating")
                    .setSingleChoiceItems(ratingLabels, selectedRatingIndex) { d, which ->
                        selectedRatingIndex = which
                        d.dismiss()
                        showEditMetadataDialog(track)
                    }
                    .show()
            }
            .show()
    }

    private fun showAddToPlaylistDialog(track: Track) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val playlists = JammerDatabase.getDatabase(requireContext())
                .playlistDao().getAll().value ?: emptyList()

            withContext(Dispatchers.Main) {
                if (playlists.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("No playlists")
                        .setMessage("Create a playlist first.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@withContext
                }

                val names = playlists.map { it.name }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("Add to playlist")
                    .setItems(names) { _, which ->
                        val playlist = playlists[which]
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val crossRef = PlaylistTrackCrossRef(
                                playlistId = playlist.id,
                                trackPath = track.path,
                                position = 0
                            )
                            JammerDatabase.getDatabase(requireContext())
                                .playlistDao().addTrack(crossRef)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    requireContext(),
                                    "Added to ${playlist.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun autoScanIfNeeded() {
        val prefs = requireContext().getSharedPreferences(
            "jammer_prefs", android.content.Context.MODE_PRIVATE
        )
        val autoScan = prefs.getBoolean("auto_scan_enabled", true)
        Log.d("LibraryFragment", "autoScan=$autoScan, triggered=$hasTriggeredScan")

        if (autoScan && !hasTriggeredScan) {
            hasTriggeredScan = true
            checkPermissionAndScan()
        } else if (!autoScan) {
            tvScanStatus.text = "Manual mode: tap '+' or FAB"
        }
    }

    private fun restoreSafFolders() {
        val prefs = requireContext().getSharedPreferences(
            "jammer_prefs", android.content.Context.MODE_PRIVATE
        )
        val saved = prefs.getStringSet("saf_folders", emptySet()) ?: emptySet()
        for (uriString in saved) {
            try {
                val uri = Uri.parse(uriString)
                val persisted = context?.contentResolver?.persistedUriPermissions
                    ?.any { it.uri == uri && it.isReadPermission } ?: false
                if (persisted) viewModel.scanSAF(uri)
            } catch (e: Exception) {
                Log.e("LibraryFragment", "Failed to scan saved folder: $uriString", e)
            }
        }
    }

    private fun checkPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tvScanStatus.text = "Scanning MediaStore..."
            viewModel.scanLibrary()
        } else {
            tvScanStatus.text = "Requesting permission..."
            permissionLauncher.launch(permission)
        }
    }
}
