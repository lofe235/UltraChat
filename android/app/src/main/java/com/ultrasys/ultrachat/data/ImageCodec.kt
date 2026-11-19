package com.ultrasys.ultrachat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.ultrasys.ultrachat.util.NoDelaySocketFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Локальное хранилище изображений: чат-фото и аватарки.
 *
 * Фото сжимаются до разумного размера, чтобы кадр протокола оставался
 * небольшим, а история не занимала гигабайты.
 */
object ImageCodec {
    const val MAX_CHAT_SIDE = 1024
    const val MAX_CHAT_BYTES = 300_000
    const val MAX_DOWNLOAD_BYTES = 3_000_000
    const val AVATAR_SIDE = 320

    data class EncodedPhoto(
        val bytes: ByteArray,
        val mimeType: String,
        val extension: String,
    )

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(NoDelaySocketFactory)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("bypass-tunnel-reminder", "true")
                        .build(),
                )
            }
            .build()
    }

    fun mediaDir(context: Context): File =
        File(context.filesDir, "media").apply { mkdirs() }

    /** Декодирует картинку из галереи, уменьшая до maxSide. */
    fun loadFromUri(context: Context, uri: Uri, maxSide: Int = MAX_CHAT_SIDE): Bitmap? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val side = maxOf(info.size.width, info.size.height)
                    if (side > maxSide) {
                        val ratio = maxSide.toFloat() / side
                        decoder.setTargetSize(
                            (info.size.width * ratio).toInt().coerceAtLeast(1),
                            (info.size.height * ratio).toInt().coerceAtLeast(1),
                        )
                    }
                }
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }?.let { scaleDown(it, maxSide) }
            }
        }.getOrNull()

    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** JPEG в base64, ужимаем качество, пока не влезем в лимит. */
    fun toBase64(bitmap: Bitmap, maxBytes: Int = MAX_CHAT_BYTES): String? {
        for (quality in intArrayOf(78, 65, 52, 40)) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (bytes.size <= maxBytes || quality == 40) {
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        return null
    }

    /**
     * Фото для чата: WebP заметно легче JPEG при том же качестве.
     * Эти же байты уходят на сервер и сохраняются локально.
     */
    fun encodeChatPhoto(bitmap: Bitmap, maxBytes: Int = MAX_CHAT_BYTES): EncodedPhoto? {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        for (quality in intArrayOf(80, 68, 56, 44)) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(format, quality, out)
                out.toByteArray()
            }
            if (bytes.isEmpty()) continue
            if (bytes.size <= maxBytes || quality == 44) {
                return EncodedPhoto(
                    bytes = bytes,
                    mimeType = "image/webp",
                    extension = "webp",
                )
            }
        }
        return null
    }

    fun saveToFile(context: Context, bitmap: Bitmap, name: String): File? = runCatching {
        val file = File(mediaDir(context), name)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        file
    }.getOrNull()

    fun saveBase64(context: Context, base64: String, name: String): File? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val file = File(mediaDir(context), name)
        file.outputStream().use { it.write(bytes) }
        file
    }.getOrNull()

    fun saveBytes(context: Context, bytes: ByteArray, name: String): File? = runCatching {
        if (bytes.isEmpty() || bytes.size > MAX_DOWNLOAD_BYTES) return null
        val file = File(mediaDir(context), name)
        file.outputStream().use { it.write(bytes) }
        file
    }.getOrNull()

    /** Скачивает фото, присланное через сервер, и кладёт его в локальное хранилище. */
    fun downloadToFile(context: Context, url: String, name: String): File? {
        val target = File(mediaDir(context), name)
        if (target.exists() && target.length() > 0) return target
        // Туннель иногда отвечает 502 на первый запрос после WebSocket-кадра.
        // Поэтому пробуем несколько раз, прежде чем показать «фото недоступно».
        repeat(3) { attempt ->
            val file = fetchToFile(context, url, name)
            if (file != null && file.length() > 0) return file
            if (attempt < 2) Thread.sleep(500L * (attempt + 1))
        }
        return null
    }

    private fun fetchToFile(context: Context, url: String, name: String): File? = runCatching {
        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) return null
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > MAX_DOWNLOAD_BYTES) return null
            saveBytes(context, bytes, name)
        }
    }.getOrNull()

    fun readBase64(path: String): String? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    fun loadBitmap(path: String?, maxSide: Int = MAX_CHAT_SIDE): Bitmap? = runCatching {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)
    }.getOrNull()

    /** Квадратная обрезка по центру (для аватарки из фото). */
    fun centerSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Сохраняет обрезанную аватарку (квадрат) в файл и возвращает путь. */
    fun saveAvatar(context: Context, bitmap: Bitmap): File? =
        saveToFile(context, scaleDown(bitmap, AVATAR_SIDE), "avatar_${System.currentTimeMillis()}.jpg")

    /** Транспортная версия аватарки: маленький квадрат в base64. */
    fun avatarPayload(path: String?): String? {
        val bitmap = loadBitmap(path, 512) ?: return null
        val square = scaleDown(centerSquare(bitmap), 128)
        return toBase64(square, 90_000)
    }

    fun drawableFrom(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    fun circleBitmap(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(
            android.graphics.PorterDuff.Mode.SRC_IN,
        )
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}
