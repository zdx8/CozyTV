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
import app.cozytv.data.LauncherPreferences
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
    private var wallpaperBitmaps: List<Bitmap> = emptyList()
    private var carouselIndex = 0

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 30_000)
        }
    }

    private val carousel = object : Runnable {
        override fun run() {
            val images = wallpaperBitmaps
            val view = imageView
            if (images.size < 2 || view == null) return
            val next = (carouselIndex + 1) % images.size
            view.animate().cancel()
            view.animate()
                .alpha(0f)
                .setDuration(700)
                .withEndAction {
                    imageView?.setImageBitmap(images[next])
                    imageView?.alpha = 0f
                    imageView?.animate()?.alpha(1f)?.setDuration(700)?.start()
                    carouselIndex = next
                }
                .start()
            handler.postDelayed(this, 15_000)
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
            val preferences = LauncherPreferences(applicationContext)
            val gallery = BingWallpaperRepository.loadGallery(
                context = applicationContext,
                count = 3,
                startIndex = preferences.wallpaperGalleryStart,
            )
            if (gallery.isNotEmpty()) {
                wallpaperBitmaps = gallery.map { it.image }
                carouselIndex = 0
                imageView?.setImageBitmap(wallpaperBitmaps.first())
                if (wallpaperBitmaps.size > 1) {
                    handler.postDelayed(carousel, 15_000)
                }
            }
        }
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(carousel)
        scope?.cancel()
        scope = null
        imageView?.animate()?.cancel()
        imageView?.setImageDrawable(null)
        wallpaperBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        wallpaperBitmaps = emptyList()
        carouselIndex = 0
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
