package com.zirakusa.reloj

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.net.toUri
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.SoundFiles
import com.zirakusa.reloj.data.SoundKind
import java.io.File

/** The phone's alarm tones and the user's own sounds («Sus sonidos»: copies inside Reloj, so they keep working). */
object Sounds {
    private fun dir(context: Context) = File(context.filesDir, "sounds").apply { mkdirs() }

    fun system(context: Context): List<Sound> = runCatching {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = manager.cursor
        val list = ArrayList<Sound>()
        while (cursor.moveToNext()) list += Sound(SoundKind.SYSTEM, manager.getRingtoneUri(cursor.position).toString(),
            cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX))
        list.distinctBy { it.name }
    }.getOrDefault(emptyList())

    fun defaultUri(context: Context): Uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    fun defaultName(context: Context): String? = runCatching { RingtoneManager.getRingtone(context, defaultUri(context))?.getTitle(context) }.getOrNull()

    fun custom(context: Context): List<Sound> = dir(context).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name.lowercase() }
        .map { Sound(SoundKind.CUSTOM, Uri.fromFile(it).toString(), it.nameWithoutExtension) }

    /** Copies a picked file (from Filem or another app) into Reloj; null when it cannot be read or is too big. */
    fun import(context: Context, uri: Uri): Sound? = runCatching {
        val resolver = context.contentResolver
        val shown = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "sonido"
        val target = SoundFiles.unique(SoundFiles.clean(shown)) { File(dir(context), it).exists() }.let { File(dir(context), it) }
        resolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n
                    if (total > SoundFiles.MAX_BYTES) { out.close(); target.delete(); return null }
                    out.write(buffer, 0, n)
                }
            }
        }
        Sound(SoundKind.CUSTOM, Uri.fromFile(target).toString(), target.nameWithoutExtension)
    }.getOrNull()

    fun remove(context: Context, sound: Sound) {
        if (sound.kind != SoundKind.CUSTOM) return
        val file = sound.uri.toUri().path?.let(::File) ?: return
        if (file.parentFile?.canonicalPath == dir(context).canonicalPath) file.delete()
    }

    fun uri(context: Context, sound: Sound): Uri? = when (sound.kind) {
        SoundKind.SILENT -> null
        SoundKind.DEFAULT -> defaultUri(context)
        else -> sound.uri.toUri()
    }
}

/** Plays a sound on the alarm stream, looping; [rising] starts soft and reaches full volume in 30 s. */
class Player(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun play(sound: Sound, rising: Boolean = false, loop: Boolean = true) {
        stop()
        val uri = Sounds.uri(context, sound) ?: return
        player = open(uri) ?: open(Sounds.defaultUri(context)) ?: return
        player?.isLooping = loop
        if (rising) rise(0) else player?.setVolume(1f, 1f)
        player?.start()
    }

    private fun open(uri: Uri): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(context, uri)
            prepare()
        }
    }.getOrNull()

    private fun rise(step: Int) {
        val v = (0.15f + 0.85f * step / 30f).coerceAtMost(1f)
        player?.setVolume(v, v)
        if (step < 30) handler.postDelayed({ rise(step + 1) }, 1000)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
    }
}
