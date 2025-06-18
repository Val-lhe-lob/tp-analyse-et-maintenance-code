package com.simplecity.amp_library.playback

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.simplecity.amp_library.playback.constants.ExternalIntents
import com.simplecity.amp_library.utils.AnalyticsManager
import com.simplecity.amp_library.utils.SettingsManager

class BluetoothManager(
    private val playbackManager: PlaybackManager,
    private val analyticsManager: AnalyticsManager,
    private val musicServiceCallbacks: MusicService.Callbacks,
    private val settingsManager: SettingsManager
) {

    private var bluetoothReceiver: BroadcastReceiver? = null

    private var a2dpReceiver: BroadcastReceiver? = null

    fun registerBluetoothReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        }

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                val extras = intent.extras ?: return

                fun shouldPause(state: Int, prevState: Int, disconnectState: Int, connectedState: Int): Boolean {
                    return (state == disconnectState || state == BluetoothA2dp.STATE_DISCONNECTING) && prevState == connectedState
                }

                fun shouldResume(state: Int, connectedState: Int): Boolean {
                    return state == connectedState
                }

                if (settingsManager.bluetoothPauseDisconnect) {
                    when (action) {
                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                            val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                            val prev = extras.getInt(BluetoothA2dp.EXTRA_PREVIOUS_STATE)
                            if (shouldPause(state, prev, BluetoothA2dp.STATE_DISCONNECTED, BluetoothA2dp.STATE_CONNECTED)) {
                                analyticsManager.dropBreadcrumb(TAG, "BT A2DP disconnect – pausing. State: $state")
                                playbackManager.pause(false)
                            }
                        }
                        BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                            val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                            val prev = extras.getInt(BluetoothHeadset.EXTRA_PREVIOUS_STATE)
                            if (shouldPause(state, prev, BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED)) {
                                analyticsManager.dropBreadcrumb(TAG, "BT Headset audio disconnect – pausing. State: $state")
                                playbackManager.pause(false)
                            }
                        }
                    }
                }

                if (settingsManager.bluetoothResumeConnect) {
                    when (action) {
                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                            val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                            if (shouldResume(state, BluetoothA2dp.STATE_CONNECTED)) {
                                playbackManager.play()
                            }
                        }
                        BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                            val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                            if (shouldResume(state, BluetoothHeadset.STATE_AUDIO_CONNECTED)) {
                                playbackManager.play()
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun unregisterBluetoothReceiver(context: Context) {
        context.unregisterReceiver(bluetoothReceiver)
    }

    fun registerA2dpServiceListener(context: Context) {
        a2dpReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action != null && action == ExternalIntents.PLAY_STATUS_REQUEST) {
                    musicServiceCallbacks.notifyChange(ExternalIntents.PLAY_STATUS_RESPONSE)
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(ExternalIntents.PLAY_STATUS_REQUEST)
        context.registerReceiver(a2dpReceiver, intentFilter)
    }

    fun unregisterA2dpServiceListener(context: Context) {
        context.unregisterReceiver(a2dpReceiver)
    }

    fun sendPlayStateChangedIntent(context: Context, extras: Bundle) {
        val intent = Intent(ExternalIntents.AVRCP_PLAY_STATE_CHANGED)
        intent.putExtras(extras)
        context.sendBroadcast(intent)
    }

    fun sendMetaChangedIntent(context: Context, extras: Bundle) {
        val intent = Intent(ExternalIntents.AVRCP_META_CHANGED)
        intent.putExtras(extras)
        context.sendBroadcast(intent)
    }

    companion object {
        const val TAG = "BluetoothManager"
    }
}
