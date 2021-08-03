package com.example.pip

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Rational
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pip.databinding.ActivityCustomBinding
import com.example.pip.widget.MovieView

/** This activity is used to display your own custom pip_mode view action */
class Custom : AppCompatActivity() {

    private val pictureInPictureParamsBuilder = PictureInPictureParams.Builder()

    /** Receives action item events from pip mode */
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                if (intent.action != ACTION_MEDIA_CONTROL) return
                when (intent.getIntExtra(CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY -> binding.movie.play()
                    CONTROL_TYPE_PAUSE -> binding.movie.pause()
                }
            }
        }
    }

    private val labelPlay: String by lazy { getString(R.string.play) }
    private val labelPause: String by lazy { getString(R.string.pause) }

    private val movieListener = object : MovieView.MovieListener() {
        override fun onMovieStarted() {
            updatePIPAction(
                R.drawable.ic_baseline_pause_24,
                labelPause,
                CONTROL_TYPE_PAUSE, REQUEST_PAUSE)
        }

        override fun onMovieStopped() {
            updatePIPAction(
                R.drawable.ic_baseline_play_arrow_24,
                labelPlay,
                CONTROL_TYPE_PLAY, REQUEST_PLAY
            )
        }

        override fun onMovieMinimized() {
            minimize()
        }
    }

    internal fun updatePIPAction(
        @DrawableRes toggle: Int,
        title: String,
        controlType: Int,
        requestCode: Int,
    ) {
        val actions = ArrayList<RemoteAction>()

        val intent = PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(ACTION_MEDIA_CONTROL).putExtra(CONTROL_TYPE, controlType),
            0
        )

        val icon = Icon.createWithResource(this@MainActivity, toggle)

        actions.add(RemoteAction(icon, title, title, intent))
        
        pictureInPictureParamsBuilder.setActions(actions)

        setPictureInPictureParams(pictureInPictureParamsBuilder.build())

    }

    internal fun minimize(){
        binding.movie.hideControls()
        pictureInPictureParamsBuilder.setAspectRatio(Rational(binding.movie.width, binding.movie.height))
        enterPictureInPictureMode(pictureInPictureParamsBuilder.build())
    }

    private fun adjustFullScreen(config: Configuration){
        val insetsController = ViewCompat.getWindowInsetsController(window.decorView)
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    private lateinit var binding : ActivityCustomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.movie.setMovieListener(movieListener)
        binding.pip.setOnClickListener { minimize() }
    }

    override fun onStop() {
        binding.movie.pause()
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        if(!isInPictureInPictureMode){
            binding.movie.showControls()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            adjustFullScreen(resources.configuration)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Starts receiving events from action items in PiP mode.
            registerReceiver(mReceiver, IntentFilter(ACTION_MEDIA_CONTROL))
        } else {
            // Out of pip mode, stop receiving events from it.
            unregisterReceiver(mReceiver)
            if (!binding.movie.isPlaying) {
                binding.movie.showControls()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustFullScreen(newConfig)
    }

    companion object {
        private const val ACTION_MEDIA_CONTROL = "media_control"
        private const val CONTROL_TYPE = "control_type"
        private const val REQUEST_PLAY = 1
        private const val REQUEST_PAUSE = 2
        private const val CONTROL_TYPE_PLAY = 1
        private const val CONTROL_TYPE_PAUSE = 2
    }
}
