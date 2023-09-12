package com.wangzhen.blur.samples

import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AppCompatActivity
import com.wangzhen.blur.samples.databinding.ActivityMainBinding

/**
 * MainActivity
 * Created by wangzhen on 2023/8/21
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            icon.startAnimation(RotateAnimation(
                0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 4000
                repeatCount = Animation.INFINITE
                interpolator = LinearInterpolator()
            })
        }
    }
}