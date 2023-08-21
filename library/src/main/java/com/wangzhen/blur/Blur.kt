package com.wangzhen.blur

import android.content.Context
import android.graphics.Bitmap

/**
 * BlurImpl
 *
 * @author : zhen51.wang
 * @date : 2023/8/21/021
 */
interface Blur {
    fun prepare(context: Context, buffer: Bitmap?, radius: Float): Boolean
    fun blur(input: Bitmap?, output: Bitmap?)
    fun release()
}