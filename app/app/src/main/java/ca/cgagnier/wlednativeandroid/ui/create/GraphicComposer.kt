package ca.cgagnier.wlednativeandroid.ui.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 自定义图文里可以叠加的内容类型。 */
enum class GraphicLayerType { TEXT, TIME, IMAGE }

/** 文字/图片旋转角度，对应上游滚动字幕的 Rotate 参数（0/90/180/270）。 */
enum class GraphicRotation(val degrees: Int) {
    NONE(0),
    ROT90(90),
    ROT180(180),
    ROT270(270),
}

/** 图层叠加方式，取自 WLED 段落的 Blend mode（bm）。 */
enum class GraphicBlend(val mode: PorterDuff.Mode?) {
    TOP(null),
    ADD(PorterDuff.Mode.ADD),
    SCREEN(PorterDuff.Mode.SCREEN),
    MULTIPLY(PorterDuff.Mode.MULTIPLY),
    LIGHTEN(PorterDuff.Mode.LIGHTEN),
    DARKEN(PorterDuff.Mode.DARKEN),
    OVERLAY(PorterDuff.Mode.OVERLAY),
}

/**
 * 一条可叠加的内容。
 *
 * 区域用百分比（0..100）描述，对应 WLED 段落的 Start/Stop X、Start/Stop Y：
 * 也就是这一层能点亮的灯珠矩形范围，与具体灯珠数量无关。
 * [sizePercent] 对文字是字号、对图片是区域内宽度占比。
 * [gradientEnd] 非空时文字用 [color] → [gradientEnd] 的横向渐变（对应 Gradient 开关）。
 */
data class GraphicLayer(
    val id: Long,
    val type: GraphicLayerType,
    val text: String = "",
    val color: Int = 0xFFFFFF,
    val startXPercent: Int = 0,
    val stopXPercent: Int = 100,
    val startYPercent: Int = 0,
    val stopYPercent: Int = 100,
    val sizePercent: Int = 50,
    val imageUri: String? = null,
    val rotation: GraphicRotation = GraphicRotation.NONE,
    val gradientEnd: Int? = null,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    val transpose: Boolean = false,
    val blend: GraphicBlend = GraphicBlend.TOP,
)

/** 栅格化后的一帧：行优先的 `0xRRGGBB`，0 表示不发光。 */
data class GraphicFrame(val colors: List<Int>, val delayCentis: Int)

/**
 * 全局播放参数，对应上游 PixelForge 的公共选项。
 *
 * [reverse] = Reverse（反向滚动）、[trailPercent] = Trail（拖尾残影）、
 * [darkCutoff] = Dark Pixel Cutoff（暗部裁剪）、[background] = Background Color。
 */
data class GraphicOptions(
    val scrollSpeed: Float = 0.6f,
    val reverse: Boolean = false,
    val trailPercent: Int = 0,
    val darkCutoff: Int = 0,
    val background: Int = 0x000000,
)

/**
 * 把文字 / 时间 / 图片叠加渲染成灯珠矩阵。
 *
 * 固件自带的滚动字幕只带 5×7 ASCII 字库，中文与 Emoji 无法显示；这里改为
 * 「App 侧用系统字体栅格化 → GIF → 上传给 Image 效果」，因此中文、Emoji、
 * 自定义图片都能显示。内容比图层区域宽时按 1 个灯珠步进生成多帧 GIF，实现跑马灯。
 *
 * 对齐上游 `pixelforge.htm` / `mode_2Dscrollingtext` 的能力：
 * - `#DATE` / `#TIME` / `#DDMM` … 时间函数占位符（含 `0` 前缀补零后缀）
 * - 图层矩形区域（Start/Stop X/Y）、镜像、转置、叠加模式
 * - 单图层旋转 0/90/180/270、横向渐变文字
 * - 全局反向滚动、拖尾、暗部裁剪与背景色
 * - 图片图层若为 GIF，会按自身时长采样成多帧播放（对齐 Image 效果）
 */
object GraphicComposer {

    /**
     * 超采样倍数：含图片的帧把每个灯珠画成 8×8 再取平均，图片细节不容易丢。
     * 纯文字/时间帧改为 1× 直接栅格化，笔画正好 1 颗灯珠宽（见 [renderFrames]）。
     */
    private const val SUPERSAMPLE = 8

    /** GIF 动图采样间隔（毫秒）。 */
    private const val ANIM_FRAME_DELAY_MS = 90

    /** 单次渲染最多生成的 GIF 帧数，避免长内容把内存吃满。 */
    private const val MAX_FRAMES = 96

    private val bitmapCache = HashMap<String, Bitmap?>()
    private val movieCache = HashMap<String, Movie?>()

    /** 文字里可用的函数占位符，投送时替换成当前时间。 */
    fun resolveText(raw: String, now: Date = Date()): String {
        fun fmt(pattern: String): String =
            SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        val braces = raw
            .replace("{time}", fmt("HH:mm"))
            .replace("{date}", fmt("yyyy-MM-dd"))
            .replace("{week}", fmt("EEE"))
            .replace("{year}", fmt("yyyy"))
            .replace("{month}", fmt("MM"))
            .replace("{day}", fmt("dd"))
            .replace("{hour}", fmt("HH"))
            .replace("{minute}", fmt("mm"))
        return replaceHashTokens(braces, now)
    }

    /**
     * 上游滚动字幕的 `#TOKEN` 语法；`#DATE0` 这类带 `0` 后缀的写法表示补零。
     * 按长度从长到短匹配，避免 `#MO` 抢占 `#MON`。
     */
    private val HASH_TOKENS = listOf(
        "DATE", "DDMM", "MMDD", "TIME", "HHMM", "YYYY", "MONL", "DDDD",
        "MON", "DAY", "YY", "HH", "MM", "SS", "MO", "DD",
    )

    private fun replaceHashTokens(raw: String, now: Date): String {
        if ('#' !in raw) return raw
        fun fmt(pattern: String): String =
            SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (raw[i] != '#') {
                out.append(raw[i])
                i++
                continue
            }
            var end = i + 1
            while (end < raw.length && end - i <= 6 && raw[end].isLetterOrDigit()) end++
            val token = raw.substring(i + 1, end).uppercase(Locale.ROOT)
            val key = HASH_TOKENS.firstOrNull { token.startsWith(it) }
            if (key == null) {
                out.append('#')
                i++
                continue
            }
            val zero = token.length > key.length && token[key.length] == '0'
            out.append(
                when (key) {
                    "DATE" -> if (zero) fmt("dd.MM.yyyy") else fmt("d.M.yyyy")
                    "DDMM" -> if (zero) fmt("dd.MM") else fmt("d.M")
                    "MMDD" -> if (zero) fmt("MM/dd") else fmt("M/d")
                    "TIME" -> if (zero) fmt("HH:mm:ss") else fmt("H:mm:ss")
                    "HHMM" -> if (zero) fmt("HH:mm") else fmt("H:mm")
                    "YYYY" -> fmt("yyyy")
                    "MONL" -> fmt("MMMM")
                    "DDDD" -> fmt("EEEE")
                    "MON" -> fmt("MMM")
                    "DAY" -> fmt("EEE")
                    "YY" -> fmt("yy")
                    "HH" -> if (zero) fmt("HH") else fmt("H")
                    "MM" -> if (zero) fmt("mm") else fmt("m")
                    "SS" -> if (zero) fmt("ss") else fmt("s")
                    "MO" -> if (zero) fmt("MM") else fmt("M")
                    "DD" -> if (zero) fmt("dd") else fmt("d")
                    else -> ""
                },
            )
            i = i + 1 + key.length + if (zero) 1 else 0
        }
        return out.toString()
    }

    /**
     * @param cols 设备矩阵列数（宽度方向的灯珠数）
     * @param rows 设备矩阵行数
     */
    fun renderFrames(
        context: Context,
        cols: Int,
        rows: Int,
        layers: List<GraphicLayer>,
        options: GraphicOptions = GraphicOptions(),
        now: Date = Date(),
    ): List<GraphicFrame> {
        if (cols <= 0 || rows <= 0) return emptyList()
        val hasImage = layers.any { it.type == GraphicLayerType.IMAGE }
        // 图片层按 8× 超采样保留细节，下采样成灯珠色块后再叠文字；
        // 文字/时间层始终按 1 颗灯珠栅格化，笔画不会被撑粗（图文混排也一样）。
        val imageCell = if (hasImage) SUPERSAMPLE else 1
        val imageWidth = cols * imageCell
        val imageHeight = rows * imageCell
        val textPaints = layers.map { buildPaint(it, rows, 1) }
        val imagePaints = if (hasImage) layers.map { buildPaint(it, imageHeight, imageCell) } else emptyList()

        val scrollCounts = layers.mapIndexed { index, layer ->
            if (layer.type == GraphicLayerType.IMAGE) 1
            else scrollFrameCount(layer, textPaints[index], cols, rows, now, 1)
        }
        val animFrames = layers.filter { it.type == GraphicLayerType.IMAGE }
            .maxOfOrNull { gifFrameCount(context, it.imageUri) } ?: 1
        val frameCount = max(
            animFrames,
            scrollCounts.maxOrNull() ?: 1,
        ).coerceIn(1, MAX_FRAMES)

        val delay = (12f - options.scrollSpeed.coerceIn(0f, 1f) * 8f)
            .roundToInt().coerceIn(4, 12)
        val background = 0xFF000000.toInt() or (options.background and 0xFFFFFF)

        val imageBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        val imageCanvas = Canvas(imageBitmap)
        val imagePixels = IntArray(imageWidth * imageHeight)
        val ledBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        val ledPixels = IntArray(cols * rows)
        val frames = ArrayList<GraphicFrame>(frameCount)

        for (frameIndex in 0 until frameCount) {
            // 1) 先把图片层画在 8× 画布上，下采样成灯珠色块
            val base: List<Int> = if (hasImage) {
                imageCanvas.drawColor(background)
                layers.forEachIndexed { index, layer ->
                    if (layer.type != GraphicLayerType.IMAGE) return@forEachIndexed
                    val paint = imagePaints[index]
                    paint.xfermode = layer.blend.mode?.let { PorterDuffXfermode(it) }
                    val rect = regionOf(layer, imageWidth, imageHeight)
                    imageCanvas.save()
                    imageCanvas.clipRect(rect)
                    if (layer.mirrorX || layer.mirrorY) {
                        imageCanvas.translate(rect.centerX(), rect.centerY())
                        imageCanvas.scale(
                            if (layer.mirrorX) -1f else 1f,
                            if (layer.mirrorY) -1f else 1f,
                        )
                        imageCanvas.translate(-rect.centerX(), -rect.centerY())
                    }
                    drawImage(imageCanvas, context, layer, rect, frameIndex * ANIM_FRAME_DELAY_MS, paint)
                    imageCanvas.restore()
                }
                imageBitmap.getPixels(imagePixels, 0, imageWidth, 0, 0, imageWidth, imageHeight)
                sampleGrid(imagePixels, imageWidth, rows, cols, imageCell, options)
            } else {
                List(cols * rows) { options.background and 0xFFFFFF }
            }

            // 2) 灯珠色块作为底，再按 1× 叠文字层
            ledBitmap.setPixels(
                IntArray(cols * rows) { 0xFF000000.toInt() or (base[it] and 0xFFFFFF) },
                0, cols, 0, 0, cols, rows,
            )
            val ledCanvas = Canvas(ledBitmap)
            layers.forEachIndexed { index, layer ->
                if (layer.type == GraphicLayerType.IMAGE) return@forEachIndexed
                val paint = textPaints[index]
                paint.xfermode = layer.blend.mode?.let { PorterDuffXfermode(it) }
                val rect = regionOf(layer, cols, rows)
                val local = if (scrollCounts[index] > 1) {
                    // 帧数超过上限时按多灯珠步进，保证内容仍能完整滚完一圈
                    val stride = ceil(scrollCounts[index] / frameCount.toFloat())
                        .toInt().coerceAtLeast(1)
                    val period = ceil(scrollCounts[index] / stride.toFloat()).toInt().coerceAtLeast(1)
                    frameIndex % period * stride
                } else {
                    0
                }
                ledCanvas.save()
                ledCanvas.clipRect(rect)
                if (layer.mirrorX || layer.mirrorY) {
                    ledCanvas.translate(rect.centerX(), rect.centerY())
                    ledCanvas.scale(
                        if (layer.mirrorX) -1f else 1f,
                        if (layer.mirrorY) -1f else 1f,
                    )
                    ledCanvas.translate(-rect.centerX(), -rect.centerY())
                }
                drawText(
                    canvas = ledCanvas,
                    layer = layer,
                    paint = paint,
                    rect = rect,
                    offsetPx = local + 1,
                    reverse = options.reverse,
                    now = now,
                    preview = false,
                )
                ledCanvas.restore()
            }
            ledBitmap.getPixels(ledPixels, 0, cols, 0, 0, cols, rows)
            frames += GraphicFrame(
                colors = ledPixels.map { it and 0xFFFFFF },
                delayCentis = delay,
            )
        }
        imageBitmap.recycle()
        ledBitmap.recycle()

        return applyTrail(frames, options.trailPercent)
    }

    /** 供界面预览：内容溢出时居中显示，避免第一帧是空白。 */
    fun renderPreview(
        context: Context,
        cols: Int,
        rows: Int,
        layers: List<GraphicLayer>,
        options: GraphicOptions = GraphicOptions(),
        now: Date = Date(),
    ): List<Int> {
        if (cols <= 0 || rows <= 0) return emptyList()
        val hasImage = layers.any { it.type == GraphicLayerType.IMAGE }
        val imageCell = if (hasImage) SUPERSAMPLE else 1
        val imageWidth = cols * imageCell
        val imageHeight = rows * imageCell
        val textPaints = layers.map { buildPaint(it, rows, 1) }
        val imagePaints = if (hasImage) layers.map { buildPaint(it, imageHeight, imageCell) } else emptyList()
        val background = 0xFF000000.toInt() or (options.background and 0xFFFFFF)

        val base: List<Int> = if (hasImage) {
            val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            layers.forEachIndexed { index, layer ->
                if (layer.type != GraphicLayerType.IMAGE) return@forEachIndexed
                val paint = imagePaints[index]
                paint.xfermode = layer.blend.mode?.let { PorterDuffXfermode(it) }
                val rect = regionOf(layer, imageWidth, imageHeight)
                canvas.save()
                canvas.clipRect(rect)
                if (layer.mirrorX || layer.mirrorY) {
                    canvas.translate(rect.centerX(), rect.centerY())
                    canvas.scale(
                        if (layer.mirrorX) -1f else 1f,
                        if (layer.mirrorY) -1f else 1f,
                    )
                    canvas.translate(-rect.centerX(), -rect.centerY())
                }
                drawImage(canvas, context, layer, rect, 0, paint)
                canvas.restore()
            }
            val pixels = IntArray(imageWidth * imageHeight)
            bitmap.getPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight)
            bitmap.recycle()
            sampleGrid(pixels, imageWidth, rows, cols, imageCell, options)
        } else {
            List(cols * rows) { options.background and 0xFFFFFF }
        }

        val ledBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        ledBitmap.setPixels(
            IntArray(cols * rows) { 0xFF000000.toInt() or (base[it] and 0xFFFFFF) },
            0, cols, 0, 0, cols, rows,
        )
        val ledCanvas = Canvas(ledBitmap)
        layers.forEachIndexed { index, layer ->
            if (layer.type == GraphicLayerType.IMAGE) return@forEachIndexed
            val paint = textPaints[index]
            paint.xfermode = layer.blend.mode?.let { PorterDuffXfermode(it) }
            val rect = regionOf(layer, cols, rows)
            ledCanvas.save()
            ledCanvas.clipRect(rect)
            if (layer.mirrorX || layer.mirrorY) {
                ledCanvas.translate(rect.centerX(), rect.centerY())
                ledCanvas.scale(
                    if (layer.mirrorX) -1f else 1f,
                    if (layer.mirrorY) -1f else 1f,
                )
                ledCanvas.translate(-rect.centerX(), -rect.centerY())
            }
            drawText(
                canvas = ledCanvas,
                layer = layer,
                paint = paint,
                rect = rect,
                offsetPx = 0,
                reverse = options.reverse,
                now = now,
                preview = true,
            )
            ledCanvas.restore()
        }
        val pixels = IntArray(cols * rows)
        ledBitmap.getPixels(pixels, 0, cols, 0, 0, cols, rows)
        ledBitmap.recycle()
        return pixels.map { it and 0xFFFFFF }
    }

    /** 图层区域：把百分比换成画布像素矩形；Transpose 交换 X/Y 的定义。 */
    private fun regionOf(layer: GraphicLayer, width: Int, height: Int): RectF {
        val x1 = layer.startXPercent.coerceIn(0, 100)
        val x2 = layer.stopXPercent.coerceIn(0, 100)
        val y1 = layer.startYPercent.coerceIn(0, 100)
        val y2 = layer.stopYPercent.coerceIn(0, 100)
        val (h1, h2, v1, v2) = if (layer.transpose) {
            listOf(y1, y2, x1, x2)
        } else {
            listOf(x1, x2, y1, y2)
        }
        val left = width * min(h1, h2) / 100f
        val right = width * max(h1, h2) / 100f
        val top = height * min(v1, v2) / 100f
        val bottom = height * max(v1, v2) / 100f
        return RectF(left, top, max(right, left + 1f), max(bottom, top + 1f))
    }

    private fun layerText(layer: GraphicLayer, now: Date): String {
        val raw = when (layer.type) {
            GraphicLayerType.TIME -> if (layer.text.isBlank()) "{time}" else layer.text
            else -> layer.text
        }
        return resolveText(raw, now)
    }

    /** 需要多少帧才能让文字完整滚过区域；不溢出时 1 帧。 */
    private fun scrollFrameCount(
        layer: GraphicLayer,
        paint: Paint,
        width: Int,
        height: Int,
        now: Date,
        cell: Int,
    ): Int {
        val rect = regionOf(layer, width, height)
        val textWidth = layerText(layer, now).split("\n")
            .maxOfOrNull { paint.measureText(it) } ?: 0f
        if (textWidth <= rect.width()) return 1
        return ceil((textWidth + rect.width()) / cell).toInt().coerceAtLeast(2)
    }

    private fun buildPaint(layer: GraphicLayer, baseHeight: Int, cell: Int): Paint {
        val color = Color.rgb(
            (layer.color shr 16) and 0xFF,
            (layer.color shr 8) and 0xFF,
            layer.color and 0xFF,
        )
        val size = baseHeight * (layer.sizePercent.coerceIn(10, 100) / 100f) * 0.9f
        return Paint().apply {
            // 1× 栅格化时关掉抗锯齿，避免边缘半亮像素把笔画撑成 2–3 颗灯珠
            isAntiAlias = cell > 1
            this.color = color
            textSize = size.coerceAtLeast(cell.toFloat()) // 至少 1 颗灯珠高（8× 时 8px）
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT // 交给系统字体回退，中文与 Emoji 都能出字形
        }
    }

    private fun drawText(
        canvas: Canvas,
        layer: GraphicLayer,
        paint: Paint,
        rect: RectF,
        offsetPx: Int,
        reverse: Boolean,
        now: Date,
        preview: Boolean,
    ) {
        val text = layerText(layer, now)
        if (text.isEmpty()) return
        val lines = text.split("\n")
        val lineHeight = paint.textSize * 1.15f
        val total = lineHeight * lines.size
        val textWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val x = when {
            textWidth <= rect.width() || preview -> rect.centerX() - textWidth / 2f
            reverse -> rect.left + offsetPx - textWidth
            else -> rect.right - offsetPx
        }
        val centerY = rect.centerY()
        paint.shader = layer.gradientEnd?.let { end ->
            LinearGradient(
                rect.left,
                0f,
                rect.right,
                0f,
                Color.rgb((layer.color shr 16) and 0xFF, (layer.color shr 8) and 0xFF, layer.color and 0xFF),
                Color.rgb((end shr 16) and 0xFF, (end shr 8) and 0xFF, end and 0xFF),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.save()
        canvas.rotate(layer.rotation.degrees.toFloat(), rect.centerX(), centerY)
        var baseline = centerY - total / 2f + paint.textSize
        lines.forEach { line ->
            canvas.drawText(line, x, baseline, paint)
            baseline += lineHeight
        }
        canvas.restore()
    }

    private fun drawImage(
        canvas: Canvas,
        context: Context,
        layer: GraphicLayer,
        rect: RectF,
        frameTimeMs: Int,
        paint: Paint,
    ) {
        paint.isFilterBitmap = true
        val maxWidth = rect.width() * (layer.sizePercent.coerceIn(10, 100) / 100f)
        canvas.save()
        canvas.rotate(layer.rotation.degrees.toFloat(), rect.centerX(), rect.centerY())
        val movie = loadMovie(context, layer.imageUri)
        if (movie != null) {
            val movieWidth = movie.width().coerceAtLeast(1)
            val movieHeight = movie.height().coerceAtLeast(1)
            val scale = min(maxWidth / movieWidth, rect.height() / movieHeight)
            val targetWidth = movieWidth * scale
            val targetHeight = movieHeight * scale
            val left = rect.centerX() - targetWidth / 2f
            val top = rect.centerY() - targetHeight / 2f
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            movie.setTime(frameTimeMs)
            movie.draw(canvas, 0f, 0f, paint)
        } else {
            val bitmap = loadBitmap(context, layer.imageUri)
            if (bitmap != null) {
                val scale = min(maxWidth / bitmap.width, rect.height() / bitmap.height)
                val targetWidth = bitmap.width * scale
                val targetHeight = bitmap.height * scale
                val left = rect.centerX() - targetWidth / 2f
                val top = rect.centerY() - targetHeight / 2f
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(left, top, left + targetWidth, top + targetHeight),
                    paint,
                )
            }
        }
        canvas.restore()
    }

    private fun loadBitmap(context: Context, uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        if (bitmapCache.containsKey(uri)) return bitmapCache[uri]
        val bitmap = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
        bitmapCache[uri] = bitmap
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun loadMovie(context: Context, uri: String?): Movie? {
        if (uri.isNullOrBlank() || !isGif(context, uri)) return null
        if (movieCache.containsKey(uri)) return movieCache[uri]
        val movie = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                Movie.decodeStream(stream)
            }
        }.getOrNull()
        movieCache[uri] = movie
        return movie
    }

    private fun isGif(context: Context, uri: String): Boolean {
        val type = runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull()
        return type == "image/gif" || uri.substringAfterLast('.').equals("gif", ignoreCase = true)
    }

    /** GIF 图层的可播放帧数（按 90ms 一帧采样，上限 [MAX_FRAMES]）。 */
    @Suppress("DEPRECATION")
    private fun gifFrameCount(context: Context, uri: String?): Int {
        val movie = loadMovie(context, uri) ?: return 1
        val duration = movie.duration()
        if (duration <= 0) return 1
        return (duration / ANIM_FRAME_DELAY_MS).coerceIn(1, MAX_FRAMES)
    }

    /** 拖尾：上一帧按比例衰减后与当前帧取较大值，形成余晖。 */
    private fun applyTrail(frames: List<GraphicFrame>, trailPercent: Int): List<GraphicFrame> {
        val trail = trailPercent.coerceIn(0, 90)
        if (trail == 0 || frames.size <= 1) return frames
        val keep = (100 - trail) / 100f
        var previous: List<Int>? = null
        return frames.map { frame ->
            val blended = previous?.let { prev ->
                frame.colors.mapIndexed { index, color ->
                    maxChannels(fade(prev.getOrElse(index) { 0 }, keep), color)
                }
            } ?: frame.colors
            previous = blended
            frame.copy(colors = blended)
        }
    }

    private fun fade(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).toInt()
        val g = (((color shr 8) and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    private fun maxChannels(a: Int, b: Int): Int {
        val r = max((a shr 16) and 0xFF, (b shr 16) and 0xFF)
        val g = max((a shr 8) and 0xFF, (b shr 8) and 0xFF)
        val bl = max(a and 0xFF, b and 0xFF)
        return (r shl 16) or (g shl 8) or bl
    }

    /**
     * 图片层的 8×8 块平均下采样，补一点亮度抵消细节损失；
     * 最后应用 Dark Pixel Cutoff 与 Background Color（对应 PixelForge 的输出处理）。
     * 文字层不走这里 —— 它是下采样之后按 1 颗灯珠直接栅格化的。
     */
    private fun sampleGrid(
        pixels: IntArray,
        width: Int,
        rows: Int,
        cols: Int,
        cell: Int,
        options: GraphicOptions,
    ): List<Int> {
        val out = IntArray(cols * rows)
        val count = cell * cell
        val cutoff = options.darkCutoff.coerceIn(0, 255)
        val background = options.background and 0xFFFFFF
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until cell) {
                    val rowStart = (row * cell + dy) * width + col * cell
                    for (dx in 0 until cell) {
                        val color = pixels[rowStart + dx]
                        r += (color shr 16) and 0xFF
                        g += (color shr 8) and 0xFF
                        b += color and 0xFF
                    }
                }
                r = boost(r / count)
                g = boost(g / count)
                b = boost(b / count)
                val dark = r < cutoff && g < cutoff && b < cutoff
                out[row * cols + col] = if (dark) background else (r shl 16) or (g shl 8) or b
            }
        }
        return out.toList()
    }

    /** 图片层 8×8 块平均后细节会偏暗，补 1.5 倍亮度。 */
    private fun boost(value: Int): Int = min(255, value * 3 / 2)
}
