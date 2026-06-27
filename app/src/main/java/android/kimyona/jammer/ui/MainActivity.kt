package android.kimyona.jammer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import android.kimyona.jammer.R
import android.kimyona.jammer.core.media.AlbumArtLoader
import android.kimyona.jammer.ui.fragments.LibraryFragment
import android.kimyona.jammer.ui.fragments.PlayerFragment
import android.kimyona.jammer.ui.fragments.QueueFragment
import android.kimyona.jammer.ui.popup.HtmlPopupActivity
import android.kimyona.jammer.ui.settings.SettingsActivity
import android.kimyona.jammer.ui.viewmodel.PlayerViewModel
import org.woheller69.freeDroidWarn.FreeDroidWarn

/**
 * MainActivity evoluída — com Mini-Player persistente e integração completa.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var viewModel: PlayerViewModel

    // Mini Player
    private lateinit var miniPlayer: View
    private lateinit var ivMiniCover: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var btnMiniPrev: ImageButton
    private lateinit var btnMiniNext: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
     // FREE ANDROID WARNING DND
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)
        
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this).get(PlayerViewModel::class.java)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        setupViewPager()
        setupTabs()
        setupMiniPlayer()
        observeViewModel()

        // Se abriu sem track, já mostra a aba da biblioteca
        viewPager.currentItem = 0
    }

    private fun setupViewPager() {
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment =
                when (position) {
                    0 -> LibraryFragment()
                    1 -> PlayerFragment()
                    2 -> QueueFragment()
                    else -> LibraryFragment()
                }
        }
    }

    private fun setupTabs() {
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Library"
                    tab.setIcon(R.drawable.ic_play)
                }
                1 -> {
                    tab.text = "Now Playing"
                    tab.setIcon(R.drawable.ic_pause)
                }
                2 -> {
                    tab.text = "Queue"
                    tab.setIcon(R.drawable.ic_queue)
                }
            }
        }.attach()
    }

    // ======== MINI PLAYER ==========

    private fun setupMiniPlayer() {
        miniPlayer = findViewById(R.id.miniPlayer)
        ivMiniCover = findViewById(R.id.ivMiniCover)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        btnMiniPrev = findViewById(R.id.btnMiniPrev)
        btnMiniNext = findViewById(R.id.btnMiniNext)

        // Click no mini-player abre a aba do player
        miniPlayer.setOnClickListener {
            viewPager.currentItem = 1
        }

        btnMiniPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        btnMiniPrev.setOnClickListener { viewModel.skipPrevious() }
        btnMiniNext.setOnClickListener { viewModel.skipNext() }
    }

    private fun observeViewModel() {
        // Mostra/esconde mini-player baseado se tem track tocando
        viewModel.showMiniPlayer.observe(this) { show ->
            miniPlayer.visibility = if (show) View.VISIBLE else View.GONE
        }

        // Atualiza info do mini-player
        viewModel.currentTrack.observe(this) { track ->
            if (track != null) {
                tvMiniTitle.text = track.title
                tvMiniArtist.text = track.artistsJoined?.replace(";", ", ")
                    ?: track.artist
                    ?: "Unknown Artist"

                AlbumArtLoader.loadThumbnail(
                    this,
                    track.path,
                    ivMiniCover,
                    R.drawable.album_placeholder,
                    48
                )
            }
        }

        // Atualiza play/pause
        viewModel.isPlaying.observe(this) { playing ->
            btnMiniPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }

    // =========== MENU =============

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_tutorial -> {
                openHtmlPopup("Tutorial", HtmlPopupActivity.ASSET_TUTORIAL)
                true
            }
            R.id.menu_faq -> {
                openHtmlPopup("FAQ", HtmlPopupActivity.ASSET_FAQ)
                true
            }
            R.id.menu_sync -> {
                viewModel.scanLibrary()
                Toast.makeText(this, "Syncing library...", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openHtmlPopup(title: String, asset: String) {
        val intent = Intent(this, HtmlPopupActivity::class.java).apply {
            putExtra(HtmlPopupActivity.EXTRA_TITLE, title)
            putExtra(HtmlPopupActivity.EXTRA_HTML_ASSET, asset)
        }
        startActivity(intent)
    }
}
