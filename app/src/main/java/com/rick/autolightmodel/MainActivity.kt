package com.rick.autolightmodel

import android.graphics.Color
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.BarUtils
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class MainActivity : AppCompatActivity(), ViewTreeObserver.OnDrawListener {

    /**
     * 性能优化：单线程池，更新阻塞时只做最后一次更新
     */
    private val executor by lazy {
        object : ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1)) {
            override fun execute(command: Runnable?) {
                queue.clear()
                super.execute(command)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BarUtils.transparentStatusBar(this) // 需要沉浸状态栏，才能截屏至状态栏
        window.decorView.viewTreeObserver.addOnDrawListener(this)
    }

    override fun onDestroy() {
        window.decorView.viewTreeObserver.removeOnDrawListener(this)
        super.onDestroy()
    }

    override fun onDraw() {
        executor.execute {
            try {
                // 获取状态栏像素
                val pixels = getStatusBarPixels()
                // 计算平均色值
                val avgColor = getAvgColor(pixels)
                // 判断是否为亮色
                val isLight = isLightColor(avgColor)
                runOnUiThread {
                    // 设置 LightModel
                    if (!isDestroyed) BarUtils.setStatusBarLightMode(this, isLight)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 获取状态栏像素
     */
    private fun getStatusBarPixels() = window.decorView.let {
        it.isDrawingCacheEnabled = true
        it.buildDrawingCache()
        // 截屏
        val screenBitmap = it.getDrawingCache()
        val width = screenBitmap.width
        val height = BarUtils.getStatusBarHeight()
        val pixels = IntArray(width * height)
        // 获取状态栏区域像素
        screenBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        it.destroyDrawingCache()
        pixels
    }

    /**
     * 获取平均色值
     */
    private fun getAvgColor(pixels: IntArray): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        pixels.forEach {
            r += Color.red(it)
            g += Color.green(it)
            b += Color.blue(it)
        }
        r /= pixels.size
        g /= pixels.size
        b /= pixels.size
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    /**
     * 是否为亮色
     */
    private fun isLightColor(@ColorInt color: Int) =
        (computeLuminance(color) + 0.05).pow(2.0) > 0.15

    /**
     * 颜色亮度
     */
    private fun computeLuminance(@ColorInt color: Int) =
        0.2126 * linearizeColorComponent(Color.red(color)) +
                0.7152 * linearizeColorComponent(Color.green(color)) +
                0.0722 * linearizeColorComponent(Color.blue(color))

    /**
     * 线性化颜色分量
     */
    private fun linearizeColorComponent(colorComponent: Int) = (colorComponent / 255.0).let {
        if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
    }
}