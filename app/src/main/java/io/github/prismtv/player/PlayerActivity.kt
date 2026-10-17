package io.github.prismtv.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.prismtv.AppGraph
import io.github.prismtv.ui.theme.PrismTheme

class PlayerActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    var inPip by mutableStateOf(false)
        private set

    /** Shared with the Compose UI so remote keys can bring the controls back. */
    var controlsVisible by mutableStateOf(true)

    /** Bumped when a remote key wakes the controls, so the UI can move focus to Play. */
    var remoteWake by mutableIntStateOf(0)
        private set

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        if (AppGraph.user.settings.startLandscape && savedInstanceState == null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val data by AppGraph.user.data.collectAsStateWithLifecycle()
            PrismTheme(data.settings, forceDark = true) {
                PlayerScreen(vm, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY)
        when {
            key != null -> vm.open(key, intent.getIntExtra(EXTRA_SOURCE, 0))
            intent.action == Intent.ACTION_VIEW && intent.data != null ->
                vm.openUrl(intent.data.toString(), intent.getStringExtra(Intent.EXTRA_TITLE) ?: intent.getStringExtra("title"))
        }
    }

    fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun toggleOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // region brightness & volume (driven by the vertical swipe gestures)

    fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0) return b
        return runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
    }

    fun currentVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).toInt(), 0)
    }

    // endregion

    // region picture in picture

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val ui = vm.ui.value
        val ratio = if (ui.videoWidth > 0 && ui.videoHeight > 0) {
            val r = ui.videoWidth.toFloat() / ui.videoHeight
            when {
                r > 2.39f -> Rational(239, 100)
                r < 0.42f -> Rational(42, 100)
                else -> Rational(ui.videoWidth, ui.videoHeight)
            }
        } else Rational(16, 9)
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(AppGraph.user.settings.autoPip && vm.player.isPlaying)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature("android.software.picture_in_picture")) return
        runCatching { pipParams()?.let { enterPictureInPictureMode(it) } }
    }

    fun refreshPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { pipParams()?.let { setPictureInPictureParams(it) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && AppGraph.user.settings.autoPip && vm.player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        // Closing the PiP window stops the activity without bringing it back: finish playback.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) finish()
    }

    // endregion

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureModeCompat()) vm.onBackground()
    }

    private fun isInPictureInPictureModeCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    /**
     * Remote control: while the controls are hidden, any D-pad key brings them back (left/right skip
     * 10 seconds when there is something to skip through). This runs before the views see the key,
     * so nothing in the hierarchy can swallow it.
     */
    // android.app.Activity.dispatchKeyEvent is public API; lint only trips over AndroidX's restricted override.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val dpad = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT
        if (dpad && !controlsVisible && !inPip) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val ui = vm.ui.value
                val canSkip = ui.timeshift || ui.recording || !ui.isLive
                when {
                    canSkip && code == KeyEvent.KEYCODE_DPAD_LEFT -> vm.seekBy(-10_000)
                    canSkip && code == KeyEvent.KEYCODE_DPAD_RIGHT -> vm.seekBy(10_000)
                    else -> { controlsVisible = true; remoteWake++ }
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> vm.togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> vm.resume()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> vm.pause()
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> vm.next()
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> vm.previous()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> vm.seekBy(10_000)
            KeyEvent.KEYCODE_MEDIA_REWIND -> vm.seekBy(-10_000)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_SOURCE = "source"

        fun start(ctx: Context, key: String, source: Int) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_KEY, key)
                    .putExtra(EXTRA_SOURCE, source)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
