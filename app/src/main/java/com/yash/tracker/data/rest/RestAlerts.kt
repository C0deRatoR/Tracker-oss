package com.yash.tracker.data.rest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yash.tracker.MainActivity
import com.yash.tracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What tells you rest is over when you are not looking at the phone.
 *
 * Between sets the screen is usually off and the phone is on a bench, which is the one moment
 * this app has to reach out rather than be read. Two ways, because either alone misses: the
 * notification counts down where the clock is, and the buzz lands when it hits zero.
 *
 * Everything here is best-effort. Notifications can be switched off per app and the permission
 * refused outright, and a phone can have no vibrator at all — none of that is worth an error
 * mid-workout, so each path checks and returns quietly.
 */
@Singleton
class RestAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val service = context.getSystemService(VibratorManager::class.java)
            service?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    }

    /** Whether the countdown can be shown at all. Vibration never needs permission. */
    private val canNotify: Boolean
        get() = manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                )

    /** The rest clock, as it stands. Called once a second while a timer is running. */
    fun counting(remainingSec: Int, totalSec: Int) {
        if (!canNotify) return
        ensureChannel()

        val notification = base()
            .setContentTitle("Rest — ${clock(remainingSec)}")
            .setContentText("Next set in ${clock(remainingSec)}")
            // Ongoing so it cannot be swiped away mid-rest, and silent so a per-second
            // update does not make a per-second noise.
            .setOngoing(true)
            .setSilent(true)
            .setProgress(totalSec.coerceAtLeast(1), totalSec - remainingSec, false)
            // The three things worth doing with a running rest, without picking the phone up.
            .addAction(0, "Open", openApp())
            .addAction(0, "+${RestActionReceiver.ADDED_SECONDS}s", command(RestActionReceiver.ACTION_ADD_TIME))
            .addAction(0, "Skip", command(RestActionReceiver.ACTION_SKIP))
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /**
     * One of the last three seconds. A short tap, so the run-in is felt as three counts.
     */
    fun countingDown() {
        buzz(TICK)
    }

    /**
     * Rest is over: buzz, and swap the countdown for something that can be dismissed.
     *
     * Two long pulses after the three short ones — a three-two-one and then go. A single buzz
     * on a bench reads as any other notification; this one has to mean get up.
     */
    fun finished() {
        buzz(GO)

        if (!canNotify) return
        ensureChannel()

        val notification = base()
            .setContentTitle("Rest over")
            .setContentText("Time for the next set.")
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** Nothing is resting any more — the timer was skipped, the set unticked, or we left. */
    fun clear() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun base(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_equipment_dumbbell)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp())

    /** Back to the session, which is what tapping the body of the notification should do. */
    private fun openApp(): PendingIntent {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * A button press, delivered to [RestActionReceiver].
     *
     * Each action gets its own request code, or the second would reuse the first's intent and
     * both buttons would do the same thing.
     */
    private fun command(action: String): PendingIntent {
        val intent = Intent(context, RestActionReceiver::class.java).setAction(action)

        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "The countdown between sets."
            setShowBadge(false)
            // The app does its own buzzing at zero; the channel staying quiet is what lets
            // the per-second update be invisible.
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun clock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "%d:%02d".format(safe / 60, safe % 60)
    }

    private fun buzz(pattern: LongArray) {
        vibrator?.let { motor ->
            runCatching { motor.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
        }
    }

    private companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 4101

        /** One count of the three-two-one: wait, short tap. */
        val TICK = longArrayOf(0, 90)

        /** And go: wait, long, gap, long. */
        val GO = longArrayOf(0, 380, 140, 520)
    }
}
