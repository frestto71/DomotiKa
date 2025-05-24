package com.example.domotika

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.widget.Toast

class CameraPlayerActivity : AppCompatActivity() {
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_player)

        val videoLayout = findViewById<VLCVideoLayout>(R.id.vlc_video_layout)

        // Recibe datos desde el intent
        val ip = intent.getStringExtra("ip") ?: ""
        val puerto = intent.getStringExtra("puerto") ?: ""
        val usuario = intent.getStringExtra("usuario") ?: ""
        val contrasena = intent.getStringExtra("contrasena") ?: ""
        val linkDirecto = intent.getStringExtra("linkDirecto") ?: ""

        val streamUrl: String = when {
            linkDirecto.isNotEmpty() -> {
                // Si hay link directo, lo usamos
                linkDirecto
            }
            ip.isNotEmpty() && puerto.isNotEmpty() -> {
                // Construimos la url RTSP
                "rtsp://$usuario:$contrasena@$ip:$puerto"
            }
            else -> {
                Toast.makeText(this, "Faltan datos para conectar la cámara", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        libVLC = LibVLC(this)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(videoLayout, null, false, false)

        try {
            val media = Media(libVLC, Uri.parse(streamUrl))
            mediaPlayer.media = media
            mediaPlayer.play()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
    }
}
