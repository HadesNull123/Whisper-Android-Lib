package com.hadtun.whisperfinal.asr


import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

class Player(private val context: Context) {

    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackStopped()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playbackListener: PlaybackListener? = null

    fun setListener(listener: PlaybackListener) {
        this.playbackListener = listener
    }

    fun initializePlayer(filePath: String) {
        val waveFileUri = Uri.parse(filePath)
        if (waveFileUri == null || context == null) {
            Log.e("WavePlayer", "File path or context is null. Cannot initialize MediaPlayer.")
            return
        }

        releaseMediaPlayer() // Release any existing MediaPlayer

        mediaPlayer = MediaPlayer.create(context, waveFileUri)
        mediaPlayer?.let { player ->
            player.setOnPreparedListener { mp ->
                playbackListener?.onPlaybackStarted()
                mp.start()
            }

            player.setOnCompletionListener { mp ->
                playbackListener?.onPlaybackStopped()
                releaseMediaPlayer()
            }
        } ?: run {
            playbackListener?.onPlaybackStopped()
        }
    }

    fun startPlayback() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                playbackListener?.onPlaybackStarted()
            }
        }
    }

    fun stopPlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
                playbackListener?.onPlaybackStopped()
                releaseMediaPlayer()
            }
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            player.release()
            mediaPlayer = null
        }
    }
}
