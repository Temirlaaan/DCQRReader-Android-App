package kz.tcloud.dcinv.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactile + audible confirmation of a successful scan — in a noisy hall, with
 * gloves on, "it beeped and buzzed" means the code registered without looking
 * at the screen (like a checkout scanner).
 */
object ScanFeedback {

    fun success(context: Context) {
        vibrate(context, 60)
        beep(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    /** Distinct cue for a reconciliation mismatch: longer buzz + lower nack tone. */
    fun warn(context: Context) {
        vibrate(context, 200)
        beep(ToneGenerator.TONE_PROP_NACK, 300)
    }

    private fun vibrate(context: Context, ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    private fun beep(tone: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(tone, durationMs)
            // Release after the tone finishes; the generator holds an audio resource.
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, (durationMs + 100).toLong())
        }
    }
}
