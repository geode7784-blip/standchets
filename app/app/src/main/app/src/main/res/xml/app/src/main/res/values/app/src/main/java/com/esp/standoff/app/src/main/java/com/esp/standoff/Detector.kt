package com.esp.standoff

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class Detection(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val confidence: Float
)

class Detector(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 640
    private val numBoxes = 8400

    init {
        val model = loadModel(context, "best.tflite")
        interpreter = Interpreter(model)
    }

    private fun loadModel(context: Context, name: String): ByteBuffer {
        val file = context.assets.openFd(name)
        val stream = FileInputStream(file.fileDescriptor)
        val channel = stream.channel
        val buffer = channel.map(
            FileChannel.MapMode.READ_ONLY,
            file.startOffset,
            file.declaredLength
        )
        return buffer
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val input = preprocess(bitmap)
        val output = Array(1) { Array(numBoxes) { FloatArray(84) } }
        interpreter.run(input, output)

        val results = mutableListOf<Detection>()
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        for (box in output[0]) {
            val conf = box[4]
            if (conf < 0.45f) continue
            var maxScore = 0f
            for (i in 5 until 84) {
                if (box[i] > maxScore) maxScore = box[i]
            }
            val score = conf * maxScore
            if (score < 0.45f) continue

            val cx = box[0] / inputSize * w
            val cy = box[1] / inputSize * h
            val bw = box[2] / inputSize * w
            val bh = box[3] / inputSize * h

            results.add(
                Detection(
                    cx - bw / 2, cy - bh / 2,
                    cx + bw / 2, cy + bh / 2,
                    score
                )
            )
        }
        return nms(results)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buffer.putFloat(((p shr 16 and 0xFF) / 255.0f))
            buffer.putFloat(((p shr 8 and 0xFF) / 255.0f))
            buffer.putFloat(((p and 0xFF) / 255.0f))
        }
        buffer.rewind()
        return buffer
    }

    private fun nms(dets: List<Detection>, iouThreshold: Float = 0.45f): List<Detection> {
        val sorted = dets.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(it, best) > iouThreshold }
        }
        return result
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.x1, b.x1)
        val y1 = maxOf(a.y1, b.y1)
        val x2 = minOf(a.x2, b.x2)
        val y2 = minOf(a.y2, b.y2)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return inter / (areaA + areaB - inter)
    }
}
