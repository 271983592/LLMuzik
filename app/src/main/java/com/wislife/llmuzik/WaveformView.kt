package com.wislife.llmuzik

import android.content.Context
import android.graphics.*
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class WaveformView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    // 渐变填充/描边/发光画笔
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 8f
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }

    private val path = Path()
    private val mirrorPath = Path()
    private var shader: LinearGradient? = null

    private var bytes: ByteArray? = null
    private var visualizer: Visualizer? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // 为了使用发光效果
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // 垂直方向的线性渐变
        shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF6750A4.toInt(), 0xFF9D74FF.toInt(), 0x33FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = bytes ?: return
        val centerY = height / 2f
        val maxAmp = height * 0.35f // 最大振幅
        val count = data.size

        path.reset()
        mirrorPath.reset()

        // 起点
        path.moveTo(0f, centerY)
        mirrorPath.moveTo(0f, centerY)

        // 采样步进，数据多的时候隔点取，保证效率
        val step = max(1, count / width)
        var x = 0f
        for (i in 0 until count step step) {
            val v = (data[i].toInt() and 0xFF) - 128 // 以128为中心
            val norm = v / 128f                       // -1~1
            val y = centerY - norm * maxAmp
            path.lineTo(x, y)
            mirrorPath.lineTo(x, centerY + norm * maxAmp)
            x += 1f
        }

        // 封闭底部，方便填充
        path.lineTo(width.toFloat(), centerY)
        mirrorPath.lineTo(width.toFloat(), centerY)

        // 先画发光，再画描边，再填充
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(mirrorPath, glowPaint)

        canvas.drawPath(path, strokePaint)
        canvas.drawPath(mirrorPath, strokePaint)

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(mirrorPath, fillPaint)
    }

    /**
     * 绑定到指定的音频会话，开始采集波形
     */
    fun attachToSession(sessionId: Int) {
        if (sessionId <= 0) return
        release()
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform?.let {
                            bytes = it
                            postInvalidateOnAnimation()
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        // 不用 FFT，这里忽略
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
    }

    /**
     * 释放 Visualizer，避免内存泄漏
     */
    fun release() {
        visualizer?.release()
        visualizer = null
        bytes = null
        invalidate()
    }
}
