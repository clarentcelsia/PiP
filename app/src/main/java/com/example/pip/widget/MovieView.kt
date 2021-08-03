package com.example.pip.widget

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.RelativeLayout
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import com.example.pip.R
import com.example.pip.databinding.MovieViewItemBinding
import java.io.IOException
import java.lang.ref.WeakReference

/** Custom Control for controlling the play/pause , forward/backward time */
class MovieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): RelativeLayout(context, attrs, defStyleAttr) {

    companion object{
        private const val TAG = "MovieView"

        /** Video stepping forward or backward */
        private const val FAST_FORWARD_BACKWARD_INTERVAL = 10000

        /** The control will be fade out */
        private const val TIMEOUT_CONTROL = 3000L
    }

    private val binding: MovieViewItemBinding

    init{
        setBackgroundColor(Color.BLACK)

        binding = MovieViewItemBinding.inflate(LayoutInflater.from(context), this)

        applyAttrs(attrs, defStyleAttr)

        val listener = OnClickListener { view->
            when(view.id){
                R.id.surface -> toggleControls()
                R.id.pause -> toggle()
                R.id.forward -> fastForward()
                R.id.rewind -> fastRewind()
                R.id.minimize -> movieListener?.onMovieMinimized()
            }

            //Hide controls when the player is starting
            mediaPlayer?.let { player->
                if(timeoutHandler == null){
                    timeoutHandler = TimeoutHandler(this@MovieView)
                }
                timeoutHandler?.let { handler->
                    handler.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS)
                    if (player.isPlaying){
                        handler.sendEmptyMessageDelayed(TimeoutHandler.MESSAGE_HIDE_CONTROLS, TIMEOUT_CONTROL)
                    }
                }
            }
        }

        binding.surface.setOnClickListener(listener)
        binding.pause.setOnClickListener(listener)
        binding.forward.setOnClickListener(listener)
        binding.rewind.setOnClickListener(listener)
        binding.minimize.setOnClickListener(listener)

        binding.surface.holder.addCallback(object : SurfaceHolder.Callback{
            @RequiresApi(Build.VERSION_CODES.N)
            override fun surfaceCreated(holder: SurfaceHolder) {
                openVideo(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mediaPlayer?.let { savedCurrentPosition = it.currentPosition }
                closeVideo()
            }

        })

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mediaPlayer?.let { player->
            val videoHeight = player.videoHeight
            val videoWidth = player.videoWidth
            if(videoHeight != 0 && videoWidth != 0){
                val aspectRatio = videoHeight.toFloat() / videoWidth
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = MeasureSpec.getSize(heightMeasureSpec)
                val widthMode = MeasureSpec.getMode(widthMeasureSpec)
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)
                if(adjustViewBounds) {
                    if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                        super.onMeasure(
                            widthMeasureSpec,
                            MeasureSpec.makeMeasureSpec((width * aspectRatio).toInt(),
                                MeasureSpec.EXACTLY))
                    } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                        super.onMeasure(
                            MeasureSpec.makeMeasureSpec((height / aspectRatio).toInt(),
                                MeasureSpec.EXACTLY),
                            heightMeasureSpec)
                    } else {
                        super.onMeasure(
                            widthMeasureSpec,
                            MeasureSpec.makeMeasureSpec((width * aspectRatio).toInt(),
                                MeasureSpec.EXACTLY))
                    }
                }else{
                    val viewRatio = height.toFloat() / width
                    if (aspectRatio > viewRatio) {
                        val padding = ((width - height / aspectRatio) / 2).toInt()
                        setPadding(padding, 0, padding, 0)
                    } else {
                        val padding = ((height - width * aspectRatio) / 2).toInt()
                        setPadding(0, padding, 0, padding)
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
                return
                }
            }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        timeoutHandler?.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS)
        timeoutHandler = null
        super.onDetachedFromWindow()
    }

    /** Monitors the event , can be overridden */
    abstract class MovieListener{

        /** Called when the video is started or resumed */
        open fun onMovieStarted(){}

        /** Called when the video is paused or finished */
        open fun onMovieStopped(){}

        /** Called when the video is minimized */
        open fun onMovieMinimized(){}
    }

    fun getVideoResourceID(): Int = videoResID

    private fun setVideoResourceID(@RawRes id: Int){
        if (id == videoResID) {
            return
        }
        videoResID = id
        val surface = binding.surface.holder.surface
        if (surface != null && surface.isValid) {
            closeVideo()
            openVideo(surface)
        }
    }

    fun setAdjustViewBounds(adjustViewBounds: Boolean){
        if (this.adjustViewBounds == adjustViewBounds) {
            return
        }
        this.adjustViewBounds = adjustViewBounds
        if (adjustViewBounds) {
            background = null
        } else {
            setBackgroundColor(Color.BLACK)
        }
        requestLayout()
    }

    fun setMovieListener(movieListener: MovieListener?){ this.movieListener = movieListener }

    /** Plays the video, null when no video is set. Only visible inside the same module */
    internal var mediaPlayer: MediaPlayer? = null

    // Defines Attribute
    /** Resource ID for video to play. */
    @RawRes
    private var videoResID: Int = 0

    var title: String = ""
    private var adjustViewBounds: Boolean = false
    private var timeoutHandler: TimeoutHandler? = null
    private var movieListener: MovieListener? = null
    private var savedCurrentPosition: Int = 0

    private fun applyAttrs(attrs: AttributeSet?, defStyleAttr: Int){
        val attr = context.obtainStyledAttributes(
            attrs, R.styleable.MovieView,
            defStyleAttr, R.style.PictureInPicture_MV)

        setVideoResourceID(attr.getResourceId(R.styleable.MovieView_android_src, 0))
        setAdjustViewBounds(attr.getBoolean(R.styleable.MovieView_android_adjustViewBounds, false))
        title = attr.getString(R.styleable.MovieView_android_title)?: ""
        attr.recycle()
    }

    private fun toggleControls(){
        if(binding.foreground.visibility == View.VISIBLE){
            hideControls()
        }else{
            showControls()
        }

    }

    private fun toggle(){
        mediaPlayer?.let { if(it.isPlaying) pause() else play() }
    }

    private fun fastForward(){
        mediaPlayer?.let { it.seekTo(it.currentPosition + FAST_FORWARD_BACKWARD_INTERVAL) }
    }

    private fun fastRewind(){
        mediaPlayer?.let { it.seekTo(it.currentPosition - FAST_FORWARD_BACKWARD_INTERVAL) }
    }

    fun showControls(){
        TransitionManager.beginDelayedTransition(this)
        binding.foreground.visibility = VISIBLE
        binding.pause.visibility = VISIBLE
        binding.forward.visibility = VISIBLE
        binding.rewind.visibility = VISIBLE
        binding.minimize.visibility = VISIBLE
    }

    fun hideControls(){
        TransitionManager.beginDelayedTransition(this)
        binding.foreground.visibility = INVISIBLE
        binding.pause.visibility = INVISIBLE
        binding.forward.visibility = INVISIBLE
        binding.rewind.visibility = INVISIBLE
        binding.minimize.visibility = INVISIBLE
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying?: false

    /** Return the current position of the video if the player has not been created */
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition?: 0

    fun play(){
        if(mediaPlayer == null){
            return
        }
        mediaPlayer!!.start()
        adjustToggleState()
        keepScreenOn = true
        movieListener?.onMovieStarted()
    }

    fun pause(){
        if(mediaPlayer == null){
            adjustToggleState()
            return
        }
        mediaPlayer!!.pause()
        adjustToggleState()
        keepScreenOn = false
        movieListener?.onMovieStopped()
    }

    internal fun openVideo(surface: Surface){
        if(videoResID == 0){
            return
        }
        mediaPlayer = MediaPlayer()
        mediaPlayer?.let {
            it.setSurface(surface)
            startVideo()
        }
    }

    fun startVideo(){
        mediaPlayer?.let { player->
            player.reset()
            try {
                resources.openRawResourceFd(videoResID).use { fd->
                    player.setDataSource(fd)
                    player.setOnPreparedListener{
                        //adjust the ratio of the view
                        requestLayout()
                        if(savedCurrentPosition > 0){
                            it.seekTo(savedCurrentPosition)
                            savedCurrentPosition = 0
                        }else{
                            play()
                        }
                    }
                    player.setOnCompletionListener {
                        adjustToggleState()
                        keepScreenOn = false
                        movieListener?.onMovieStarted()
                    }
                    player.prepare()
                }

            }catch (e: IOException){
                Log.e(TAG, "startVideo: Failed to start video! ")
            }

        }
    }

    internal fun closeVideo(){
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun adjustToggleState(){
        mediaPlayer?.let { player->
            if(player.isPlaying){
                binding.pause.contentDescription = resources.getString(R.string.pause)
                binding.pause.setImageResource(R.drawable.ic_baseline_pause_24)
            }else{
                binding.pause.contentDescription = resources.getString(R.string.play)
                binding.pause.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            }
        }
    }

    private class TimeoutHandler(view: MovieView): Handler(Looper.getMainLooper()){

        private val reference: WeakReference<MovieView> = WeakReference(view)

        override fun handleMessage(message: Message){
            when(message.what){
                MESSAGE_HIDE_CONTROLS -> reference.get()?.hideControls()
                else -> super.handleMessage(message)
            }
        }

        companion object{
            const val MESSAGE_HIDE_CONTROLS = 1
        }
    }
}