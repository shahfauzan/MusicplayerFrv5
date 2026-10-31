package com.offline.player

import android.media.audiofx.Visualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Singleton Audio Visualizer & Beat Energy Manager.
 * Guarantees that at most ONE Visualizer instance exists per audio session,
 * completely preventing native AudioEffect status 6 / BAD_VALUE / DEAD_OBJECT ("err6") errors.
 */
object AudioVisualizerManager {
    private var visualizer: Visualizer? = null
    private var boundSessionId: Int = -1

    /** 36-bar resampled waveform bytes for LiveVisualizer */
    var waveBytes by mutableStateOf(ByteArray(36))
        private set

    /** RMS audio energy normalized 0.0f .. 1.0f for BeatSync */
    var energy by mutableFloatStateOf(0f)
        private set

    /** Transient beat kick drop flag for screen shake and punchy bass pulses */
    var isKickDrop by mutableStateOf(false)
        private set

    @Synchronized
    fun bind(sessionId: Int, playing: Boolean) {
        if (!playing || sessionId <= 0) {
            release()
            return
        }
        if (boundSessionId == sessionId && visualizer != null) {
            return
        }
        release()

        try {
            val range = Visualizer.getCaptureSizeRange()
            val cap = if (range != null && range.isNotEmpty()) range[0].coerceAtLeast(64) else 64
            val v = Visualizer(sessionId).apply {
                captureSize = cap
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        private var lastEnergy = 0f
                        override fun onWaveFormDataCapture(vis: Visualizer, data: ByteArray, samplingRate: Int) {
                            if (data.isEmpty()) return
                            // 1. Resample wave bars for VisualizerView
                            val b = ByteArray(36)
                            val step = (data.size / 36).coerceAtLeast(1)
                            for (i in 0 until 36) {
                                val idx = (i * step).coerceIn(0, data.lastIndex)
                                b[i] = data[idx]
                            }
                            waveBytes = b

                            // 2. RMS Energy & Kick Drop detection for BeatSync
                            var sumSq = 0.0
                            for (byteVal in data) {
                                val d = abs(byteVal.toInt() - 128)
                                sumSq += (d * d)
                            }
                            val rms = sqrt(sumSq / data.size) / 128.0
                            val e = (rms * 2.2).toFloat().coerceIn(0f, 1f)
                            energy = e
                            val delta = e - lastEnergy
                            isKickDrop = delta > 0.28f && e > 0.42f
                            lastEnergy = e
                        }

                        override fun onFftDataCapture(vis: Visualizer, fft: ByteArray, rate: Int) {}
                    },
                    Visualizer.getMaxCaptureRate() / 2, true, false
                )
                enabled = true
            }
            visualizer = v
            boundSessionId = sessionId
        } catch (e: Throwable) {
            // Catches error codes 6, -6 (DEAD_OBJECT), -4, etc. silently without crash
            release()
        }
    }

    @Synchronized
    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Throwable) {
        } finally {
            visualizer = null
            boundSessionId = -1
        }
    }
}
