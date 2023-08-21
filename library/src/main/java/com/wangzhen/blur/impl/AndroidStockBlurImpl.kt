package com.wangzhen.blur.impl

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RSRuntimeException
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.wangzhen.blur.Blur

/**
 * AndroidStockBlurImpl
 *
 * @author : zhen51.wang
 * @date : 2023/8/21/021
 */
class AndroidStockBlurImpl : Blur {
    private var mRenderScript: RenderScript? = null
    private var mBlurScript: ScriptIntrinsicBlur? = null
    private var mBlurInput: Allocation? = null
    private var mBlurOutput: Allocation? = null

    override fun prepare(context: Context, buffer: Bitmap?, radius: Float): Boolean {
        if (mRenderScript == null) {
            try {
                mRenderScript = RenderScript.create(context)
                mBlurScript =
                    ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript)).apply {
                        setRadius(radius)
                    }
            } catch (e: RSRuntimeException) {
                release()
                return false
            }
        }

        Allocation.createFromBitmap(
            mRenderScript, buffer,
            Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT
        ).also { allocation ->
            mBlurInput = allocation
            mBlurOutput = Allocation.createTyped(mRenderScript, allocation.type)
        }
        return true
    }

    override fun release() {
        mBlurInput?.destroy()
        mBlurOutput?.destroy()
        mBlurScript?.destroy()
        mRenderScript?.destroy()
    }

    override fun blur(input: Bitmap?, output: Bitmap?) {
        mBlurInput?.copyFrom(input)
        mBlurScript?.setInput(mBlurInput)
        mBlurScript?.forEach(mBlurOutput)
        mBlurOutput?.copyTo(output)
    }
}