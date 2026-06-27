package android.kimyona.jammer.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.kimyona.jammer.R
import android.kimyona.jammer.data.JammerDatabase
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * SettingsActivity — Configurações funcionais do Jammer.
 *
 * Features:
 * - Tema escuro/claro
 * - Auto-scan toggle
 * - Limpar biblioteca
 * - Limpar cache de capas
 * - Exportar/importar dados
 * - Sobre
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configuracoes"

        toolbar.setNavigationOnClickListener { finish() }

        setupSwitches()
        setupButtons()
    }

    private fun setupSwitches() {
        val prefs = getSharedPreferences("jammer_prefs", MODE_PRIVATE)

        // Auto-scan
        val switchAutoScan = findViewById<Switch>(R.id.switchAutoScan)
        switchAutoScan.isChecked = prefs.getBoolean("auto_scan_enabled", true)
        switchAutoScan.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_scan_enabled", isChecked).apply()
            Toast.makeText(this, "Auto-scan ${if (isChecked) "ativado" else "desativado"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        // Limpar Biblioteca
        findViewById<Button>(R.id.btnClearLibrary).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Limpar Biblioteca")
                .setMessage("Isso removera todas as musicas do banco de dados. Continuar?")
                .setPositiveButton("Limpar") { _, _ ->
                    lifecycleScope.launch {
                        JammerDatabase.getDatabase(this@SettingsActivity).trackDao().deleteAll()
                        JammerDatabase.getDatabase(this@SettingsActivity).queueDao().clear()
                        Toast.makeText(this@SettingsActivity, "Biblioteca limpa!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Reset Onboarding
        findViewById<Button>(R.id.btnResetOnboarding).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reiniciar Onboarding")
                .setMessage("Deseja mostrar a tela de boas-vindas novamente?")
                .setPositiveButton("Sim") { _, _ ->
                    getSharedPreferences("jammer_prefs", MODE_PRIVATE)
                        .edit().putBoolean("onboarding_complete", false).apply()
                    Toast.makeText(this, "Onboarding reiniciado!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Sobre
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            AlertDialog.Builder(this)
                .setTitle("Jammer")
                .setMessage(
                    "Versao: $version\n\n" +
                    "Seu player de musica. Sem rastreamento. Sem frescura.\n\n" +
                    "'Your player, your own deck. Jam and play.'\n\n" +
                    "Licenciado sob FOSS MultMediaX.\n" +
                    "Feito com muito amor no Brasil."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
