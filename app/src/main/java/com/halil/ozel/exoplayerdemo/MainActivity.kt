package com.halil.ozel.exoplayerdemo

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.halil.ozel.exoplayerdemo.databinding.ActivityMainBinding

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var exoPlayer: ExoPlayer? = null
    private var channelAdapter: ChannelAdapter? = null
    private var playbackPosition = 0L
    private var playWhenReady = true

    companion object {
        private const val DASH_URL = "https://media.axprod.net/TestVectors/Cmaf/protected_1080p_h264_cbcs/manifest.mpd"
        private const val LICENSE_URL = "https://drm-widevine-licensing.axtest.net/AcquireLicense"
        private const val DRM_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.ewogICJ2ZXJzaW9uIjogMSwKICAiY29tX2tleV9pZCI6ICI2OWU1NDA4OC1lOWUwLTQ1MzAtOGMxYS0xZWI2ZGNkMGQxNGUiLAogICJtZXNzYWdlIjogewogICAgInR5cGUiOiAiZW50aXRsZW1lbnRfbWVzc2FnZSIsCiAgICAidmVyc2lvbiI6IDIsCiAgICAibGljZW5zZSI6IHsKICAgICAgImFsbG93X3BlcnNpc3RlbmNlIjogdHJ1ZQogICAgfSwKICAgICJjb250ZW50X2tleXNfc291cmNlIjogewogICAgICAiaW5saW5lIjogWwogICAgICAgIHsKICAgICAgICAgICJpZCI6ICIzMDJmODBkZC00MTFlLTQ4ODYtYmNhNS1iYjFmODAxOGEwMjQiLAogICAgICAgICAgImVuY3J5cHRlZF9rZXkiOiAicm9LQWcwdDdKaTFpNDNmd3YremZ0UT09IiwKICAgICAgICAgICJ1c2FnZV9wb2xpY3kiOiAiUG9saWN5IEEiCiAgICAgICAgfQogICAgICBdCiAgICB9LAogICAgImNvbnRlbnRfa2V5X3VzYWdlX3BvbGljaWVzIjogWwogICAgICB7CiAgICAgICAgIm5hbWUiOiAiUG9saWN5IEEiLAogICAgICAgICJwbGF5cmVhZHkiOiB7CiAgICAgICAgICAibWluX2RldmljZV9zZWN1cml0eV9sZXZlbCI6IDE1MCwKICAgICAgICAgICJwbGF5X2VuYWJsZXJzIjogWwogICAgICAgICAgICAiNzg2NjI3RDgtQzJBNi00NEJFLThGODgtMDhBRTI1NUIwMUE3IgogICAgICAgICAgXQogICAgICAgIH0KICAgICAgfQogICAgXQogIH0KfQ._NfhLVY7S6k8TJDWPeMPhUawhympnrk6WAZHOVjER6M"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupPlayer()
        setupPlaylistLoader()
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter { channel ->
            playStream(channel.streamUrl)
        }
        binding.rvChannels.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = channelAdapter
        }
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            binding.playerView.player = this
            binding.playerView.defaultArtwork = BitmapFactory.decodeResource(
                resources, R.drawable.ic_launcher_background
            ) as Drawable?
        }
    }

    private fun setupPlaylistLoader() {
        binding.btnLoadPlaylist.setOnClickListener {
            val url = binding.etPlaylistUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.etPlaylistUrl.error = "Please enter a playlist URL"
                return@setOnClickListener
            }
            loadPlaylist(url)
        }
    }

    private fun loadPlaylist(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLoadPlaylist.isEnabled = false

        lifecycleScope.launchWhenStarted {
            val channels = M3UParser.parse(url)
            binding.progressBar.visibility = View.GONE
            binding.btnLoadPlaylist.isEnabled = true

            if (channels.isEmpty()) {
                Toast.makeText(this@MainActivity, "No channels found or failed to load", Toast.LENGTH_SHORT).show()
            } else {
                channelAdapter?.submitList(channels)
                Toast.makeText(this@MainActivity, "Loaded ${channels.size} channels", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playStream(streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .apply {
                when {
                    streamUrl.contains(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
                    streamUrl.contains(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            seekTo(playbackPosition, 0)
            playWhenReady = true
            prepare()
        }
    }

    private fun playDrmStream() {
        val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(LICENSE_URL)
            .setLicenseRequestHeaders(mapOf("X-AxDRM-Message" to DRM_TOKEN))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(DASH_URL)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setDrmConfiguration(drmConfig)
            .build()

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    override fun onStop() {
        super.onStop()
        savePlaybackState()
        exoPlayer?.stop()
    }

    override fun onPause() {
        super.onPause()
        savePlaybackState()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun savePlaybackState() {
        exoPlayer?.let {
            playbackPosition = it.currentPosition
            playWhenReady = it.playWhenReady
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode()
        }
    }
}