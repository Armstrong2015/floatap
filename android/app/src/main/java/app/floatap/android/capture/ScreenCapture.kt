package app.floatap.android.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds a MediaProjection session so the run loop can sample a pixel.
 * Permission must be granted from an Activity; not restored after process death.
 */
object ScreenCapture {
    @Volatile
    private var projection: MediaProjection? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var reader: ImageReader? = null

    private val latest = AtomicReference<Bitmap?>(null)
    private var width = 0
    private var height = 0
    private var densityDpi = 0
    private var worker: HandlerThread? = null
    private var handler: Handler? = null

    fun isReady(): Boolean = projection != null && reader != null

    fun start(context: Context, resultCode: Int, data: Intent): Boolean {
        stop()
        val mgr = context.getSystemService(MediaProjectionManager::class.java) ?: return false
        val proj = mgr.getMediaProjection(resultCode, data) ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi
        val thread = HandlerThread("floatap-capture").also { it.start() }
        worker = thread
        handler = Handler(thread.looper)
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer.duplicate()
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = if (bitmap.width == width) {
                    bitmap
                } else {
                    Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                        if (it != bitmap) bitmap.recycle()
                    }
                }
                latest.getAndSet(cropped)?.recycle()
            } catch (_: Exception) {
                // keep previous frame
            } finally {
                image.close()
            }
        }, handler)
        reader = imageReader
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }, handler)
        virtualDisplay = proj.createVirtualDisplay(
            "floatap-cap",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )
        return true
    }

    fun stop() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        reader = null
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        latest.getAndSet(null)?.recycle()
        worker?.quitSafely()
        worker = null
        handler = null
    }

    fun pixel(x: Int, y: Int): Int? {
        val frame = latest.get() ?: return null
        if (x !in 0 until frame.width || y !in 0 until frame.height) return null
        return try {
            frame.getPixel(x, y)
        } catch (_: Exception) {
            null
        }
    }

    fun awaitPixel(x: Int, y: Int, timeoutMs: Long = 2000): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val value = pixel(x, y)
            if (value != null) return value
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                return null
            }
        }
        return pixel(x, y)
    }
}
