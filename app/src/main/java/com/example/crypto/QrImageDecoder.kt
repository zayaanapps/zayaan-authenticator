package com.example.crypto

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * 100% offline QR code decoder using ZXing.
 * Supports both live CameraX YUV_420_888 frames and imported Bitmap images from Android Photo Picker.
 */
object QrImageDecoder {

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    fun decodeFromImageProxy(image: ImageProxy): String? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride

            val luminanceData = if (rowStride == width) {
                bytes
            } else {
                val tight = ByteArray(width * height)
                for (row in 0 until height) {
                    val srcOffset = row * rowStride
                    val dstOffset = row * width
                    if (srcOffset + width <= bytes.size) {
                        System.arraycopy(bytes, srcOffset, tight, dstOffset, width)
                    }
                }
                tight
            }

            val source = PlanarYUVLuminanceSource(
                luminanceData,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(hints) }
            reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            null
        }
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(hints) }
            reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {
            null
        }
    }
}
