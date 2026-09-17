package fi.aalto.radio.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import fi.aalto.radio.RadioStation
import java.io.File

/** Square images for car screens, notifications and the widget. */
internal object LogoTiles {
    private const val SIZE = 320
    private const val DIRECTORY = "station-tiles"

    /** The logo centred on white with a margin, so wide logos are not cropped. */
    fun square(context: Context, logo: File): File? {
        val out = File(dir(context), "sq2-${logo.nameWithoutExtension}.png")
        if (out.isFile && out.lastModified() >= logo.lastModified()) return out
        val source = BitmapFactory.decodeFile(logo.absolutePath) ?: return null
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // White, unless the logo is light on transparent (it would vanish).
        canvas.drawColor(if (isLightOnTransparent(source)) Color.rgb(27, 32, 39) else Color.WHITE)
        val margin = SIZE * 0.1f
        val box = SIZE - 2 * margin
        val scale = minOf(box / source.width, box / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val left = (SIZE - w) / 2
        val top = (SIZE - h) / 2
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            RectF(left, top, left + w, top + h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        return write(bitmap, out)
    }

    /** Station colour with its initials, like the tiles in the app. */
    fun initials(context: Context, station: RadioStation): File? {
        val out = File(dir(context), "tile-${station.id.hashCode().toUInt()}.png")
        if (out.isFile) return out
        val color = station.logoColorArgb.toInt()
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val luminance = (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255
        val text = station.initials.ifBlank { station.name.take(2) }.uppercase()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (luminance > 0.5) Color.rgb(16, 19, 23) else Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = SIZE * 0.34f
        }
        // Shrink long initials to fit.
        while (paint.measureText(text) > SIZE * 0.8f && paint.textSize > 20f) {
            paint.textSize -= 4f
        }
        val y = SIZE / 2f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, SIZE / 2f, y, paint)
        return write(bitmap, out)
    }

    private fun isLightOnTransparent(source: Bitmap): Boolean {
        val w = source.width
        val h = source.height
        val transparentCorners = listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1)
            .count { (x, y) -> Color.alpha(source.getPixel(x, y)) < 60 }
        if (transparentCorners < 3) return false
        var sum = 0.0
        var count = 0
        for (yy in 0 until 10) for (xx in 0 until 10) {
            val p = source.getPixel(xx * (w - 1) / 9, yy * (h - 1) / 9)
            if (Color.alpha(p) > 128) {
                sum += (0.2126 * Color.red(p) + 0.7152 * Color.green(p) + 0.0722 * Color.blue(p)) / 255.0
                count++
            }
        }
        return count > 0 && sum / count > 0.75
    }

    private fun dir(context: Context): File =
        File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    private fun write(bitmap: Bitmap, out: File): File? = runCatching {
        val temp = File(out.parentFile, "${out.name}.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!temp.renameTo(out)) temp.delete()
        out.takeIf { it.isFile }
    }.getOrNull()
}
