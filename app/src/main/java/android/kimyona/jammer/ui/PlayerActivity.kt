package android.kimyona.jammer.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.kimyona.jammer.R

/**
 * PlayerActivity — stub legacy.
 * O player real agora é PlayerFragment dentro de MainActivity.
 * Essa activity existe apenas pra não quebrar referências antigas no Manifest.
 */
class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Redireciona pro MainActivity
        startActivity(android.content.Intent(this, MainActivity::class.java))
        finish()
    }
}
