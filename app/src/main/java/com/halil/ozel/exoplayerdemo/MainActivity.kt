package com.halil.ozel.exoplayerdemo

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.dash.DashMediaSource
import com.halil.ozel.exoplayerdemo.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private var exoPlayer: ExoPlayer? = null
    private var playbackPosition = 0L
    private var playWhenReady = true
    private var currentChannel: ChannelInfo? = null
    private var retryCount = 0
    private val maxRetries = 3

    companion object {
        private const val TAG = "ExoPlayerDemo"
        
        // PRODUCTION-READY LIVE STREAM CONFIGURATION
        // Buffer settings for live streaming - balance between startup speed and stability
        private const val MIN_BUFFER_MS = 20000      // 20 seconds minimum (increased for stability)
        private const val MAX_BUFFER_MS = 60000      // 60 seconds maximum (more buffer for live)
        private const val BUFFER_FOR_PLAYBACK_MS = 3000  // 3 seconds to start playing
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000 // 5 seconds after rebuffer
        
        // Live configuration for production use
        private const val TARGET_LIVE_OFFSET_MS = 8000L  // 8 seconds behind live edge
        private const val MIN_LIVE_OFFSET_MS = 5000L    // Minimum 5 seconds
        private const val MAX_LIVE_OFFSET_MS = 30000L   // Maximum 30 seconds
        private const val MIN_PLAYBACK_SPEED = 0.95f    // Allow slight slowdown
        private const val MAX_PLAYBACK_SPEED = 1.05f   // Allow slight speedup
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setView()
        preparePlayer()
    }

    private fun setView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
    }

    @OptIn(UnstableApi::class)
    private fun preparePlayer() {
        Log.d(TAG, "Preparing ExoPlayer...")

        // Configure load control for live streaming
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(50 * 1024 * 1024) // 50MB target buffer
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        // Add player listener for debugging
        exoPlayer?.addListener(playerListener)

        exoPlayer?.playWhenReady = true
        binding.playerView.player = exoPlayer
        binding.playerView.defaultArtwork = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_launcher_background
        ) as Drawable?

        Log.d(TAG, "ExoPlayer created, loading channel...")

        // Test with servertvhub source (working in OTT Navigator)
        val sampleM3U8 = """
#EXTM3U
#EXTINF:-1 group-title="Entertainment" tvg-logo="https://jiotvimages.cdn.jio.com/dare_images/images/Gemini_TV_HD.png",Gemini TV HD
https://servertvhub.site/superlive/mpd.php?id=897|license_type=clearkey&license_key=https://servertvhub.site/superlive/keys.php?id=897
        """.trimIndent()

        val channels = StreamParser.parseM3U8(sampleM3U8)

        Log.d(TAG, "Parsed ${channels.size} channels")

        if (channels.isNotEmpty()) {
            val channel = channels[0]
            Log.d(TAG, "Playing channel: ${channel.name}, URL: ${channel.streamUrl}")
            playChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun playChannel(channel: ChannelInfo) {
        if (channel.streamUrl.isNullOrBlank()) {
            Log.e(TAG, "Channel URL is null or blank!")
            return
        }
        
        // Store current channel and reset retry count
        currentChannel = channel
        retryCount = 0
        
        Log.d(TAG, "Playing channel: ${channel.name}")
        Log.d(TAG, "Stream URL: ${channel.streamUrl}")
        Log.d(TAG, "License Type: ${channel.licenseType}")
        Log.d(TAG, "License Key: ${channel.licenseKey}")
        Log.d(TAG, "HTTP Headers: ${channel.httpHeaders}")
        Log.d(TAG, "User Agent: ${channel.userAgent}")

        val httpHeaders = DrmHelper.buildHttpHeaders(channel.httpHeaders)
        
        // Always set User-Agent - use channel's userAgent or default
        httpHeaders["User-Agent"] = channel.userAgent ?: "plaYtv/7.1.3 (Linux;Android 14) ExoPlayerLib/2.11.7"

        // Add common headers for Jio TV streams
        httpHeaders["Referer"] = "https://www.jiotv.com/"
        httpHeaders["Origin"] = "https://www.jiotv.com"

        Log.d(TAG, "Final HTTP Headers: $httpHeaders")

        val mediaSource = DrmHelper.createMediaSource(
            context = this,
            streamUrl = channel.streamUrl,
            licenseType = channel.licenseType,
            licenseKey = channel.licenseKey,
            httpHeaders = httpHeaders
        )

        if (mediaSource != null) {
            Log.d(TAG, "MediaSource created successfully, preparing player...")
            exoPlayer?.apply {
                setMediaSource(mediaSource)
                seekTo(playbackPosition)
                playWhenReady = this@MainActivity.playWhenReady
                prepare()
            }
        } else {
            Log.e(TAG, "Failed to create MediaSource!")
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "PlaybackState changed to: $stateName")

            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Player is buffering - waiting for data...")
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "Player is ready - playing!")
                    if (exoPlayer?.isPlaying == true) {
                        Log.d(TAG, "Video is currently playing")
                    }
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Player is idle")
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Player playback ended")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying changed to: $isPlaying")
            if (isPlaying) {
                Log.d(TAG, "Playback started!")
            } else {
                Log.d(TAG, "Playback paused/stopped")
                exoPlayer?.let { player ->
                    Log.d(TAG, "Current position: ${player.currentPosition}ms")
                    Log.d(TAG, "Playback state: ${player.playbackState}")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}")
            Log.e(TAG, "Error code: ${error.errorCode}")
            Log.e(TAG, "Error cause: ${error.cause?.message}")
            
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                    Log.e(TAG, "Network connection failed")
                }
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    Log.e(TAG, "Network connection timeout")
                }
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    Log.e(TAG, "Manifest parsing error")
                }
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                    Log.e(TAG, "Decoder initialization failed")
                }
                PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                    Log.e(TAG, "Decoding failed")
                }
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> {
                    Log.e(TAG, "DRM license acquisition failed")
                }
                else -> {
                    Log.e(TAG, "Unknown error code: ${error.errorCode}")
                }
            }
            
            // Auto-retry logic for production
            handlePlaybackError()
        }
        
        private fun handlePlaybackError() {
            if (retryCount < maxRetries && currentChannel != null) {
                retryCount++
                val delay = (retryCount * 2000L) // 2s, 4s, 6s
                Log.d(TAG, "Retrying in ${delay}ms (attempt $retryCount/$maxRetries)")
                
                binding.root.postDelayed({
                    Log.d(TAG, "Retrying playback...")
                    currentChannel?.let { playChannel(it) }
                }, delay)
            } else {
                Log.e(TAG, "Max retries reached. Playback failed.")
                retryCount = 0
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            Log.d(TAG, "Position discontinuity: ${oldPosition.positionMs} -> ${newPosition.positionMs}")
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode()
        }
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            Log.d(TAG, "Releasing player at position: ${player.currentPosition}ms")
            playbackPosition = player.currentPosition
            playWhenReady = player.playWhenReady
            player.removeListener(playerListener)
            player.release()
            exoPlayer = null
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }
}