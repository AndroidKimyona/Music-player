package android.kimyona.jammer.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AlbumArtLoader — BULLETPROOF + SAF-COMPATIBLE.
 *
 * Correções:
 * - Suporta content:// URIs (SAF) além de paths de arquivo
 * - extractEmbeddedArt é suspend (background thread)
 * - load() / loadThumbnail() usam coroutines internamente
 * - Nunca chama MediaMetadataRetriever na main thread
 * - Cache de bitmaps para notificações
 */
object AlbumArtLoader {

    private val notificationCache = androidx.collection.LruCache<String, Bitmap>(20)

    /**
     * Extrai capa embutida — SÓ chamar de background thread (suspend).
     * Suporta tanto File paths quanto content:// URIs.
     */
    suspend fun extractEmbeddedArt(context: Context, path: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrEmpty()) return@withContext null

        val retriever = MediaMetadataRetriever()
        try {
            when {
                path.startsWith("content://") -> {
                    val fd = context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
                    fd?.use {
                        retriever.setDataSource(it.fileDescriptor)
                    }
                }
                File(path).exists() -> {
                    retriever.setDataSource(path)
                }
                else -> return@withContext null
            }

            val art = retriever.embeddedPicture
            if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Carrega capa em ImageView (async, seguro para UI thread).
     * Suporta content:// URIs via Glide.
     */
    fun load(context: Context, path: String?, imageView: ImageView, placeholderRes: Int) {
        if (path.isNullOrEmpty()) {
            imageView.setImageResource(placeholderRes)
            return
        }
        try {
            val loadTarget = when {
                path.startsWith("content://") -> Uri.parse(path)
                else -> File(path)
            }
            Glide.with(context)
                .load(loadTarget)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .centerCrop()
                .into(imageView)
        } catch (e: Exception) {
            imageView.setImageResource(placeholderRes)
        }
    }

    /**
     * Carrega thumbnail com tamanho específico.
     * Suporta content:// URIs.
     */
    fun loadThumbnail(
        context: Context,
        path: String?,
        imageView: ImageView,
        placeholderRes: Int,
        sizeDp: Int = 56
    ) {
        if (path.isNullOrEmpty()) {
            imageView.setImageResource(placeholderRes)
            return
        }
        try {
            val loadTarget = when {
                path.startsWith("content://") -> Uri.parse(path)
                else -> File(path)
            }
            Glide.with(context)
                .load(loadTarget)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .override(sizeDp, sizeDp)
                .centerCrop()
                .into(imageView)
        } catch (e: Exception) {
            imageView.setImageResource(placeholderRes)
        }
    }

    /**
     * Carrega capa para notificação — com cache LruCache.
     * Suporta content:// URIs.
     */
    suspend fun loadForNotification(context: Context, path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        notificationCache.get(path)?.let { return it }
        val bitmap = extractEmbeddedArt(context, path)
        if (bitmap != null) {
            notificationCache.put(path, bitmap)
        }
        return bitmap
    }

    /**
     * Limpa cache do Glide para uma ImageView.
     */
    fun clear(imageView: ImageView) {
        try {
            Glide.with(imageView.context).clear(imageView)
        } catch (e: Exception) {
            // Ignora
        }
    }

    /**
     * Limpa cache de notificações.
     */
    fun clearNotificationCache() {
        notificationCache.evictAll()
    }
}
