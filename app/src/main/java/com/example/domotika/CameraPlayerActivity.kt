package com.example.domotika

import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

class CameraPlayerActivity : AppCompatActivity() {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var scaleFactor = 1.0f
    private val minScale = 1.0f
    private val maxScale = 4.0f

    // Variables para pan
    private var lastX = 0f
    private var lastY = 0f
    private var posX = 0f
    private var posY = 0f
    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_player)

        videoLayout = findViewById(R.id.vlc_video_layout)

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        val ip = intent.getStringExtra("ip") ?: ""
        val puerto = intent.getStringExtra("puerto") ?: ""
        val usuario = intent.getStringExtra("usuario") ?: ""
        val contrasena = intent.getStringExtra("contrasena") ?: ""
        val linkDirecto = intent.getStringExtra("linkDirecto") ?: ""

        val streamUrl: String = when {
            linkDirecto.isNotEmpty() -> {
                linkDirecto
            }
            ip.isNotEmpty() && puerto.isNotEmpty() -> {
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (scaleFactor > 1.0f) {
                    // Empezar a arrastrar solo si estamos zoom > 1
                    lastX = event.x - posX
                    lastY = event.y - posY
                    isDragging = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val newX = event.x - lastX
                    val newY = event.y - lastY

                    // Opcional: limita el movimiento para no arrastrar demasiado (según zoom y tamaño)
                    posX = newX
                    posY = newY

                    videoLayout.translationX = posX
                    videoLayout.translationY = posY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)

            videoLayout.scaleX = scaleFactor
            videoLayout.scaleY = scaleFactor

            // Resetear traslación cuando hacemos zoom out a 1x
            if (scaleFactor == 1.0f) {
                posX = 0f
                posY = 0f
                videoLayout.translationX = 0f
                videoLayout.translationY = 0f
            }

            return true
        }
    }
}
