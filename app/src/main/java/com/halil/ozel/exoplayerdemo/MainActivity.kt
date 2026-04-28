package com.halil.ozel.exoplayerdemo

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import com.halil.ozel.exoplayerdemo.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private var exoPlayer: ExoPlayer? = null
    private var playbackPosition = 0L
    private var playWhenReady = true

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
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer?.playWhenReady = true
        binding.playerView.player = exoPlayer
        binding.playerView.defaultArtwork = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_launcher_background
        ) as Drawable?

        val sampleM3U8 = """
#EXTM3U
#EXTINF:-1 group-title="Entertainment" tvg-logo="https://jiotvimages.cdn.jio.com/dare_images/images/Gemini_TV_HD.png",Gemini TV HD
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=https://alex4528.site/jplus/license
https://alex4528.site/jplus/LIll0L1iLI0IioloLIIo/manifest.mpd

#EXTINF:-1 tvg-id="SonyLIVSports1.in" tvg-name="SonyLIV Sports 1" tvg-logo="https://jiotv.catchup.cdn.jio.com/dare_images/images/SonyLIV_Sports_1.png" group-title="Sports", SonyLIV Sports 1
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=4e4c5dba5bfe5a7c9338bc086eb80379:b8e8932f440084e1d2b705fa52eef94b
#EXTHTTP:{"Cookie":"__hdnea__=st=1775213381~exp=1775234981~acl=/*~hmac=f5780b96fb3563724dcfef24dd519634fca02c67724d1f8130a3331f4290e75a"}
https://jiotvpllive.cdn.jio.com//bpk-tv/SonyLIV_Sports_1_MOB/WDVLive/index.mpd?__hdnea__=st=1775213381~exp=1775234981~acl=/*~hmac=f5780b96fb3563724dcfef24dd519634fca02c67724d1f8130a3331f4290e75a

#EXTINF:-1 group-title="Jiostar" tvg-logo="https://jiotvimages.cdn.jio.com/dare_images/images/Star_Sports_HD1.png",Star Sports 1 HD
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=965dc2ddb1d85138ad787999a7f30ca5:859695076e67fe961836b564db6d689c
#EXTVLCOPT:http-user-agent=plaYtv/7.1.5 (Linux;Android 13) ExoPlayerLib/2.11.6 YGX/69.69.69.69
#EXTHTTP:{"cookie":"__hdnea__=st=1766796373~exp=1766882773~acl=/*~hmac=ca88377115fd039646668ccd9889fa41145509d34fa3bf1ca5e5eb9b56dfc2c5&xxx=%7Ccookie=__hdnea__=st=1766796373~exp=1766882773~acl=/*~hmac=ca88377115fd039646668ccd9889fa41145509d34fa3bf1ca5e5eb9b56dfc2c5"}
https://jiotvpllive.cdn.jio.com/bpk-tv/Star_Sports_HD1_Hindi_BTS/WDVLive/playlist.mpd
        """.trimIndent()

        val channels = StreamParser.parseM3U8(sampleM3U8)

        if (channels.isNotEmpty()) {
            val channel = channels[0]
            playChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun playChannel(channel: ChannelInfo) {
        if (channel.streamUrl.isNullOrBlank()) {
            return
        }

        val httpHeaders = DrmHelper.buildHttpHeaders(channel.httpHeaders)
        if (!channel.userAgent.isNullOrBlank()) {
            httpHeaders["User-Agent"] = channel.userAgent
        }

        val mediaSource = DrmHelper.createMediaSource(
            context = this,
            streamUrl = channel.streamUrl,
            licenseType = channel.licenseType,
            licenseKey = channel.licenseKey,
            httpHeaders = httpHeaders
        )

        if (mediaSource != null) {
            exoPlayer?.apply {
                setMediaSource(mediaSource)
                seekTo(playbackPosition)
                playWhenReady = this@MainActivity.playWhenReady
                prepare()
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode()
        }
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            playbackPosition = player.currentPosition
            playWhenReady = player.playWhenReady
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