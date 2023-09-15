package com.wangzhen.blur

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import com.wangzhen.blur.impl.AndroidStockBlurImpl
import com.wangzhen.blur.impl.EmptyBlurImpl

/**
 * A realtime blurring overlay (like iOS UIVisualEffectView). Just put it above
 * the view you want to blur and it doesn't have to be in the same ViewGroup
 *
 * Created by wangzhen on 2023/8/21
 */
class DynamicBlurView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private var downSampleFactor: Float // default 4
    private var overlayColor: Int // default #aaffffff
    private var blurRadius: Float // default 10dp (0 < r <= 25)
    private var oval = false
    private var borderRadius = 0f

    private val blurImpl: Blur
    private var dirty = false
    private var mBitmapToBlur: Bitmap? = null
    private var mBlurredBitmap: Bitmap? = null
    private var blurringCanvas: Canvas? = null
    private var isRendering = false
    private val paint: Paint
    private val bitmapPaint: Paint
    private val rectSrc = Rect()
    private val rectDst = RectF()

    // mDecorView should be the root view of the activity (even if you are on a different window like a dialog)
    private var mDecorView: View? = null

    // If the view is on different root view (usually means we are on a PopupWindow),
    // we need to manually call invalidate() in onPreDraw(), otherwise we will not be able to see the changes
    private var mDifferentRoot = false


    companion object {
        private const val MAX_BLUR_RADIUS = 25
        private var RENDERING_COUNT = 0
        private var findBlurImpl = false
        private val STOP_EXCEPTION = StopException()
    }

    init {
        blurImpl = getBlurImpl() // provide your own by override getBlurImpl()
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DynamicBlurView)
        blurRadius = typedArray.getDimension(
            R.styleable.DynamicBlurView_blurRadius, TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10f, context.resources.displayMetrics
            )
        )
        downSampleFactor = typedArray.getFloat(R.styleable.DynamicBlurView_downSampleFactor, 4f)
        overlayColor =
            typedArray.getColor(R.styleable.DynamicBlurView_overlayColor, -0x55000001) // 0xAAFFFFFF

        oval = typedArray.getBoolean(R.styleable.DynamicBlurView_oval, false)
        if (!oval) {
            borderRadius = typedArray.getDimension(R.styleable.DynamicBlurView_borderRadius, 0f)
        }
        typedArray.recycle()

        paint = Paint().apply {
            color = overlayColor
        }
        bitmapPaint = Paint().apply {
            isAntiAlias = true
        }
    }

    private fun getBlurImpl(): Blur {
        if (!findBlurImpl) {
            try {
                val impl = AndroidStockBlurImpl()
                val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                impl.prepare(context, bmp, 4f)
                impl.release()
                bmp.recycle()
                findBlurImpl = true
            } catch (ignored: Throwable) {
            }
        }
        return if (findBlurImpl) {
            AndroidStockBlurImpl()
        } else EmptyBlurImpl()
    }

    fun setBlurRadius(radius: Float) {
        if (blurRadius != radius) {
            blurRadius = radius
            dirty = true
            invalidate()
        }
    }

    fun setOval(value: Boolean) {
        if (oval != value) {
            oval = value
            invalidate()
        }
    }

    fun setBorderRadius(radius: Float) {
        if (borderRadius != radius) {
            borderRadius = radius
            dirty = true
            invalidate()
        }
    }

    fun setDownSampleFactor(factor: Float) {
        require(factor > 0) { "DownSample factor must be greater than 0." }
        if (downSampleFactor != factor) {
            downSampleFactor = factor
            dirty = true // may also change blur radius
            releaseBitmap()
            invalidate()
        }
    }

    fun setOverlayColor(color: Int) {
        if (overlayColor != color) {
            overlayColor = color
            paint.color = overlayColor
            invalidate()
        }
    }

    private fun releaseBitmap() {
        mBitmapToBlur?.recycle()
        mBlurredBitmap?.recycle()
    }

    private fun release() {
        releaseBitmap()
        blurImpl.release()
    }

    private fun prepare(): Boolean {
        if (blurRadius == 0f) {
            release()
            return false
        }
        var downSampleFactor = downSampleFactor
        var radius = blurRadius / downSampleFactor
        if (radius > MAX_BLUR_RADIUS) {
            downSampleFactor = downSampleFactor * radius / MAX_BLUR_RADIUS
            radius = MAX_BLUR_RADIUS.toFloat()
        }
        val width = width
        val height = height
        val scaledWidth = Math.max(1, (width / downSampleFactor).toInt())
        val scaledHeight = Math.max(1, (height / downSampleFactor).toInt())
        if (blurringCanvas == null || mBlurredBitmap == null || mBlurredBitmap!!.width != scaledWidth || mBlurredBitmap!!.height != scaledHeight) {
            dirty = true
            releaseBitmap()
            var r = false
            try {
                mBitmapToBlur =
                    Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                mBitmapToBlur?.let { bitmap ->
                    blurringCanvas = Canvas(bitmap)
                    mBlurredBitmap =
                        Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                    if (mBlurredBitmap == null) {
                        return false
                    }
                    r = true
                } ?: let {
                    return false
                }
            } catch (e: OutOfMemoryError) {
                // Bitmap.createBitmap() may cause OOM error
                // Simply ignore and fallback
            } finally {
                if (!r) {
                    release()
                    return false
                }
            }
        }
        if (dirty) {
            dirty = if (blurImpl.prepare(context, mBitmapToBlur, radius)) {
                false
            } else {
                return false
            }
        }
        return true
    }

    private fun blur(bitmapToBlur: Bitmap?, blurredBitmap: Bitmap?) {
        blurImpl.blur(bitmapToBlur, blurredBitmap)
    }

    private val preDrawListener = OnPreDrawListener {
        val locations = IntArray(2)
        val oldBitmap = mBlurredBitmap
        mDecorView?.let { decor ->
            if (isShown && prepare()) {
                val needRedraw = mBlurredBitmap != oldBitmap
                decor.getLocationOnScreen(locations)
                var x = -locations[0]
                var y = -locations[1]
                getLocationOnScreen(locations)
                x += locations[0]
                y += locations[1]

                mBitmapToBlur?.let { bitmapToBlur ->
                    // just erase transparent
                    bitmapToBlur.eraseColor(overlayColor and 0xffffff)

                    blurringCanvas?.let { canvas ->
                        val rc = canvas.save()
                        isRendering = true
                        RENDERING_COUNT++
                        try {
                            canvas.scale(
                                1f * bitmapToBlur.width / width, 1f * bitmapToBlur.height / height
                            )
                            canvas.translate(-x.toFloat(), -y.toFloat())
                            decor.background?.draw(canvas)
                            decor.draw(blurringCanvas)
                        } catch (ignored: StopException) {
                        } finally {
                            isRendering = false
                            RENDERING_COUNT--
                            canvas.restoreToCount(rc)
                        }
                    }
                }

                blur(mBitmapToBlur, mBlurredBitmap)
                if (needRedraw || mDifferentRoot) {
                    invalidate()
                }
            }
        }
        true
    }

    private fun getActivityDecorView(): View? {
        var ctx = context
        var i = 0
        while (i < 4 && ctx !is Activity && ctx is ContextWrapper) {
            ctx = ctx.baseContext
            i++
        }
        return if (ctx is Activity) {
            ctx.window.decorView
        } else {
            null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mDecorView = getActivityDecorView()
        mDecorView?.let { decor ->
            decor.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            mDifferentRoot = decor.rootView !== rootView
            if (mDifferentRoot) {
                decor.postInvalidate()
            }
        } ?: let {
            mDifferentRoot = false
        }
    }

    override fun onDetachedFromWindow() {
        mDecorView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        release()
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        if (isRendering) {
            // Quit here, don't draw views above me
            throw STOP_EXCEPTION
        } else if (RENDERING_COUNT > 0) {
            // Doesn't support blurview overlap on another blurview
        } else {
            super.draw(canvas)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBlurredBitmap(canvas, mBlurredBitmap)
    }

    /**
     * Custom draw the blurred bitmap and color to define your own shape
     *
     * @param canvas        [Canvas]
     * @param blurredBitmap [Bitmap]
     */
    private fun drawBlurredBitmap(canvas: Canvas, blurredBitmap: Bitmap?) {
        if (blurredBitmap != null) {
            rectSrc.right = blurredBitmap.width
            rectSrc.bottom = blurredBitmap.height
            rectDst.right = width.toFloat()
            rectDst.bottom = height.toFloat()

            bitmapPaint.shader =
                BitmapShader(blurredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply {
                        setScale(
                            rectDst.width() / blurredBitmap.width,
                            rectDst.height() / blurredBitmap.height
                        )
                    })
                }
            if (oval) {
                canvas.drawOval(rectDst, bitmapPaint)
            } else {
                canvas.drawRoundRect(rectDst, borderRadius, borderRadius, bitmapPaint)
            }
        }
        if (oval) {
            canvas.drawOval(rectDst, bitmapPaint)
        } else {
            canvas.drawRoundRect(rectDst, borderRadius, borderRadius, paint)
        }
    }

    private class StopException : RuntimeException()
}