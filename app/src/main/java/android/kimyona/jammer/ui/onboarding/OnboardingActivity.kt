package android.kimyona.jammer.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.kimyona.jammer.R
import android.kimyona.jammer.ui.MainActivity

/**
 * Tela de boas-vindas / onboarding.
 * - Pede permissões (READ_MEDIA_AUDIO)
 * - Escolhe modo de scan: Automático (MediaStore) ou Manual (SAF)
 * - NÃO exige MANAGE_EXTERNAL_STORAGE — usa MediaStore + SAF
 * - Salva preferências em SharedPreferences
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var rgScanMode: RadioGroup
    private lateinit var rbAuto: RadioButton
    private lateinit var rbManual: RadioButton
    private lateinit var btnGrantPermissions: Button
    private lateinit var btnPickFolders: Button
    private lateinit var btnStart: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvPermissionInfo: TextView

    private var hasAudioPermission = false
    private var selectedScanMode = "auto"
    private val selectedFolders = mutableListOf<String>()

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        updateUI()
    }

    private val multiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions.entries.any { it.value }
        updateUI()
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedFolders.add(it.toString())
            tvStatus.text = "Pastas selecionadas: ${selectedFolders.size}"
            btnStart.isEnabled = hasAudioPermission && selectedFolders.isNotEmpty()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("jammer_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_onboarding)

        rgScanMode = findViewById(R.id.rgScanMode)
        rbAuto = findViewById(R.id.rbAuto)
        rbManual = findViewById(R.id.rbManual)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        btnPickFolders = findViewById(R.id.btnPickFolders)
        btnStart = findViewById(R.id.btnStart)
        tvStatus = findViewById(R.id.tvStatus)
        tvPermissionInfo = findViewById(R.id.tvPermissionInfo)

        rgScanMode.setOnCheckedChangeListener { _, checkedId ->
            selectedScanMode = when (checkedId) {
                R.id.rbAuto -> "auto"
                R.id.rbManual -> "manual"
                else -> "auto"
            }
            updateUI()
        }

        btnGrantPermissions.setOnClickListener { requestPermissions() }
        btnPickFolders.setOnClickListener { folderPickerLauncher.launch(null) }
        btnStart.setOnClickListener { savePreferencesAndStart() }

        updateUI()
    }

    private fun requestPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    hasAudioPermission = true
                }
            }
            else -> {
                multiPermissionLauncher.launch(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ))
            }
        }
        updateUI()
    }

    private fun updateUI() {
        val audioPermText = if (hasAudioPermission) "✅ Áudio" else "❌ Áudio"
        tvPermissionInfo.text = "Permissões: $audioPermText"

        when (selectedScanMode) {
            "auto" -> {
                btnPickFolders.visibility = View.GONE
                tvStatus.text = "Modo Automático: escaneia via MediaStore (não precisa de permissão total)"
                btnStart.isEnabled = hasAudioPermission
            }
            "manual" -> {
                btnPickFolders.visibility = View.VISIBLE
                tvStatus.text = "Modo Manual: escolha as pastas (${selectedFolders.size} selecionadas)"
                btnStart.isEnabled = hasAudioPermission && selectedFolders.isNotEmpty()
            }
        }

        btnGrantPermissions.isEnabled = !hasAudioPermission
    }

    private fun savePreferencesAndStart() {
        val prefs = getSharedPreferences("jammer_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("onboarding_complete", true)
            putString("scan_mode", selectedScanMode)
            putStringSet("selected_folders", selectedFolders.toSet())
            putBoolean("auto_scan_enabled", selectedScanMode == "auto")
            apply()
        }

        Toast.makeText(this, "Configurações salvas! Bem-vindo ao Jammer.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
