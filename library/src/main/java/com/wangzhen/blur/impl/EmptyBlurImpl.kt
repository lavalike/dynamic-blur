package com.wangzhen.blur.impl

import android.content.Context
import android.graphics.Bitmap
import com.wangzhen.blur.Blur

/**
 * EmptyBlurImpl
 *
 * @author : zhen51.wang
 * @date : 2023/8/21/021
 */
class EmptyBlurImpl : Blur {
    override fun prepare(context: Context, buffer: Bitmap?, radius: Float) = false
    override fun release() {}
    override fun blur(input: Bitmap?, output: Bitmap?) {}
}