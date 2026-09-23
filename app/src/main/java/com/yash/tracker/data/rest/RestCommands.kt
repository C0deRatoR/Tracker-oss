package com.yash.tracker.data.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What the notification's buttons ask of the timer. */
enum class RestCommand { ADD_TIME, SKIP }

/**
 * Carries a button press from the notification back to the live session.
 *
 * The timer itself lives in the session's view model, which a broadcast receiver has no handle
 * on — so the receiver posts here and the view model listens while it is alive. A replay buffer
 * of one covers the gap where a press lands microseconds before collection starts; extra
 * presses are dropped rather than queued, because a stale "skip" arriving late would end a rest
 * the user is in the middle of.
 */
@Singleton
class RestCommands @Inject constructor() {

    private val _commands = MutableSharedFlow<RestCommand>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val commands = _commands.asSharedFlow()

    fun send(command: RestCommand) {
        _commands.tryEmit(command)
    }
}

/**
 * The notification's "Add 30s" and "Skip" buttons.
 *
 * A receiver rather than an activity so pressing either leaves the phone where it is: the whole
 * point of these is not having to open the app with a bar in your hands.
 */
@AndroidEntryPoint
class RestActionReceiver : BroadcastReceiver() {

    @Inject lateinit var commands: RestCommands

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ADD_TIME -> commands.send(RestCommand.ADD_TIME)
            ACTION_SKIP -> commands.send(RestCommand.SKIP)
        }
    }

    companion object {
        const val ACTION_ADD_TIME = "com.yash.tracker.REST_ADD_TIME"
        const val ACTION_SKIP = "com.yash.tracker.REST_SKIP"

        /** What "Add time" adds. Long enough to matter, short enough to press twice. */
        const val ADDED_SECONDS = 30
    }
}
