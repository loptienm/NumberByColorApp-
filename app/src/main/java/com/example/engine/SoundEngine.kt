package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Offline PCM audio synthesizer using Android AudioTrack so sound effects work with zero external
 * audio files or network access across Android and Amazon Fire tablets.
 */
class SoundEngine {
  @Volatile var enabled: Boolean = true

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val sampleRate = 22050

  fun playPop() {
    if (!enabled) return
    scope.launch { playTone(startFreq = 320.0, endFreq = 780.0, durationMs = 80, volume = 0.25f) }
  }

  fun playWobble() {
    if (!enabled) return
    scope.launch {
      playTone(
        startFreq = 165.0,
        endFreq = 105.0,
        durationMs = 160,
        volume = 0.22f,
        sawtooth = true,
      )
    }
  }

  fun playSuccessFanfare() {
    if (!enabled) return
    scope.launch {
      val notes = listOf(261.63, 329.63, 392.0, 523.25)
      notes.forEachIndexed { idx, freq ->
        playTone(
          startFreq = freq,
          endFreq = freq,
          durationMs = if (idx == notes.lastIndex) 300 else 140,
          volume = 0.24f,
        )
      }
    }
  }

  fun vibrateColorCompleted(context: Context) {
    try {
      val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
      if (!vibrator.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(65L, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(65L)
      }
    } catch (_: Throwable) {
      // Ignore on devices without vibration support
    }
  }

  private fun playTone(
    startFreq: Double,
    endFreq: Double,
    durationMs: Int,
    volume: Float,
    sawtooth: Boolean = false,
  ) {
    try {
      val numSamples = (sampleRate * durationMs) / 1000
      if (numSamples <= 0) return
      val pcm = ShortArray(numSamples)
      var phase = 0.0

      for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val freq =
          if (startFreq == endFreq) startFreq
          else startFreq * (endFreq / startFreq).pow(t)
        phase += (2.0 * PI * freq) / sampleRate
        val wave =
          if (sawtooth) {
            2.0 * ((phase / (2.0 * PI)) % 1.0) - 1.0
          } else {
            sin(phase)
          }
        // Quick fade-in and exponential decay to prevent clicks
        val attack = (i / (sampleRate * 0.008)).coerceAtMost(1.0)
        val decay = exp(-3.2 * t)
        val sample = (wave * attack * decay * volume * Short.MAX_VALUE).toInt()
        pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
      }

      val bufferBytes = pcm.size * 2
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setTransferMode(AudioTrack.MODE_STATIC)
          .setBufferSizeInBytes(bufferBytes)
          .build()

      track.write(pcm, 0, pcm.size)
      track.play()
      Thread.sleep(durationMs.toLong() + 25L)
      track.stop()
      track.release()
    } catch (_: Throwable) {
      // Ignore audio hardware failures gracefully (e.g., muted/headless environments)
    }
  }
}
