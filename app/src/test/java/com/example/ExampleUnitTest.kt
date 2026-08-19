package com.example

import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testFFmpegMethods() {
    val ffmpeg = FFmpeg.getInstance()
    val methods = ffmpeg.javaClass.methods.map { it.name }
    println("FFmpeg methods: $methods")
    assertTrue(methods.isNotEmpty())
  }
}
