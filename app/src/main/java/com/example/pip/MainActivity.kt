package com.example.pip

import android.app.PictureInPictureParams
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pip.databinding.ActivityMainBinding
import com.example.pip.widget.MovieView

class MainActivity : AppCompatActivity() {

    private lateinit var session: MediaSessionCompat

    private val pictureInPictureParamsBuilder = PictureInPictureParams.Builder()

    private val movieListener = object : MovieView.MovieListener() {

        override fun onMovieStarted() {
            binding.movie.let { view ->
                updatePlaybackState(
                    PlaybackStateCompat.STATE_PLAYING,
                    view.getCurrentPosition(),
                    view.getVideoResourceID()
                )
            }
        }

        override fun onMovieStopped() {
            binding.movie.let { view ->
                updatePlaybackState(
                    PlaybackStateCompat.STATE_PAUSED,
                    view.getCurrentPosition(),
                    view.getVideoResourceID()
                )
            }
        }

        override fun onMovieMinimized() {
            minimize()
        }
    }

    /**
     * Overloaded method that persists previously set media actions.
     * @param state The state of the video, e.g. playing, paused, etc.
     * *
     * @param position The position of playback in the video.
     * *
     * @param mediaId The media id related to the video in the media session.
     */
    private fun updatePlaybackState(
        @PlaybackStateCompat.State state: Int,
        position: Int,
        mediaId: Int
    ) {
        val actions = session.controller.playbackState.actions
        updatePlaybackState(state, actions, position, mediaId)
    }

    private fun updatePlaybackState(
        @PlaybackStateCompat.State state: Int,
        playbackActions: Long,
        position: Int,
        mediaId: Int
    ) {
        val builder = PlaybackStateCompat.Builder()
            .setActions(playbackActions)
            .setActiveQueueItemId(mediaId.toLong())
            .setState(state, position.toLong(), 1.0f)
        session.setPlaybackState(builder.build())
    }

    private inner class MediaSessionCallback(
        private val movieView: MovieView
    ) : MediaSessionCompat.Callback() {

        private var indexInPlaylist: Int = 0

        init {
            indexInPlaylist = 1
        }

        override fun onPlay() {
            super.onPlay()
            movieView.play()
        }

        override fun onPause() {
            super.onPause()
            movieView.pause()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            movieView.startVideo()
            if (indexInPlaylist < PLAYLIST_SIZE) {
                indexInPlaylist++
                if (indexInPlaylist >= PLAYLIST_SIZE) {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceID()
                    )
                } else {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_ALL,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceID()
                    )
                }
            }
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            movieView.startVideo()
            if (indexInPlaylist > 0) {
                indexInPlaylist--
                if (indexInPlaylist <= 0) {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceID()
                    )
                } else {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        MEDIA_ACTIONS_ALL,
                        movieView.getCurrentPosition(),
                        movieView.getVideoResourceID()
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        initializeMediaSession()
    }

    private fun initializeMediaSession() {
        session = MediaSessionCompat(this, TAG)
        session.isActive = true
        MediaControllerCompat.setMediaController(this, session.controller)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, binding.movie.title)
            .build()
        session.setMetadata(metadata)

        val mMediaSessionCallback = MediaSessionCallback(binding.movie)
        session.setCallback(mMediaSessionCallback)

        val state = if (binding.movie.isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED
        updatePlaybackState(
            state,
            MEDIA_ACTIONS_ALL,
            binding.movie.getCurrentPosition(),
            binding.movie.getVideoResourceID()
        )
    }

    override fun onStop() {
        super.onStop()
        binding.movie.pause()
        session.release()
    }

    override fun onRestart() {
        super.onRestart()
        if (!isInPictureInPictureMode) binding.movie.showControls()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustFullScreen(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) adjustFullScreen(resources.configuration)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration)
    {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            if (!binding.movie.isPlaying) {
                binding.movie.showControls()
            }
        }
    }

    internal fun minimize() {
        binding.movie.hideControls()

        val aspectRatio = Rational(binding.movie.width, binding.movie.height)
        pictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build()
        enterPictureInPictureMode(pictureInPictureParamsBuilder.build())
    }

    private fun adjustFullScreen(config: Configuration) {
        val insetsController = ViewCompat.getWindowInsetsController(window.decorView)
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            binding.scroll.visibility = View.GONE
            binding.movie.setAdjustViewBounds(false)
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            binding.scroll.visibility = View.VISIBLE
            binding.movie.setAdjustViewBounds(true)
        }
    }

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.movie.setMovieListener(movieListener)
        binding.pip.setOnClickListener { minimize() }
    }

    companion object {

        private const val TAG = "MediaSessionPlayback"

        private const val MEDIA_ACTIONS_PLAY_PAUSE =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE

        private const val MEDIA_ACTIONS_ALL =
            MEDIA_ACTIONS_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        private const val PLAYLIST_SIZE = 2
    }

}