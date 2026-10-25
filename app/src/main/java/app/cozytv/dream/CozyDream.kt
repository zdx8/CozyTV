package app.cozytv.dream

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cozytv.data.BingWallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CozyDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var imageView: ImageView? = null
    private var clockView: TextView? = null
    private var dateView: TextView? = null
    private var wallpaperBitmap: Bitmap? = null

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        setFullscreen(true)
        setInteractive(false)
        setContentView(buildScene())
        updateClock()
        handler.postDelayed(tick, 30_000)

        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = jobScope
        jobScope.launch {
            BingWallpaperRepository.load(applicationContext)?.let {
                wallpaperBitmap = it.image
                imageView?.setImageBitmap(it.image)
            }
        }
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        scope?.cancel()
        scope = null
        imageView?.setImageDrawable(null)
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
        imageView = null
        clockView = null
        dateView = null
        super.onDreamingStopped()
    }

    private fun buildScene(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(101, 126, 185),
                    Color.rgb(45, 60, 96),
                    Color.rgb(13, 17, 37),
                ),
            )
        }
        root.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        imageView = image

        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    0x66000000,
                    0xAA000000.toInt(),
                ),
            )
        }
        root.addView(
            shade,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 62f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -0.04f
        }
        val date = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock)
            addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        }
        root.addView(
            words,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                leftMargin = dp(64)
                bottomMargin = dp(58)
            },
        )
        clockView = clock
        dateView = date
        return root
    }

    private fun updateClock() {
        val now = Date()
        clockView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateView?.text = SimpleDateFormat(
            "M月d日  EEEE",
            Locale.SIMPLIFIED_CHINESE,
        ).format(now)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
