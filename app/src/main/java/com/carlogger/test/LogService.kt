package com.carlogger.test

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.view.KeyEvent

class LogService : Service() {

    private var session: MediaSession? = null
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var started = false

    @Volatile
    private var running = false

    private fun log(m: String) = Logger.add(this, m)

    private val cb = object : MediaSession.Callback() {
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val ev = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            if (ev != null) {
                val act = if (ev.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
                log("KEY ${KeyEvent.keyCodeToString(ev.keyCode)} $act repeat=${ev.repeatCount}")
            }
            return super.onMediaButtonEvent(mediaButtonIntent)
        }

        override fun onPlay() = log("CB onPlay")
        override fun onPause() = log("CB onPause")
        override fun onStop() = log("CB onStop")
        override fun onSkipToNext() = log("CB onSkipToNext")
        override fun onSkipToPrevious() = log("CB onSkipToPrevious")
        override fun onFastForward() = log("CB onFastForward")
        override fun onRewind() = log("CB onRewind")
        override fun onSeekTo(pos: Long) = log("CB onSeekTo $pos")
    }

    private val volRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val type = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            val now = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            val prev = i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
            log("VOLUME stream=$type $prev -> $now")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            setup()
        }
        return START_STICKY
    }

    private fun setup() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("car", "Car Logger", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "car")
            .setContentTitle("Car Logger is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        val s = MediaSession(this, "CarLogger")
        s.setCallback(cb)
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Car Logger test")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "silence")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 3600000L)
                .build()
        )
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_FAST_FORWARD or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_SEEK_TO
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
        s.isActive = true
        session = s

        startSilence()

        registerReceiver(
            volRx,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            Context.RECEIVER_EXPORTED
        )

        val am = getSystemService(AudioManager::class.java)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        log("SERVICE started. music volume $cur/$max")
    }

    // Plays digital silence so the system and the car treat this app as the active player.
    private fun startSilence() {
        val rate = 44100
        val min = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        t.play()
        worker = Thread {
            val buf = ByteArray(4096)
            while (running) {
                t.write(buf, 0, buf.size)
            }
        }
        worker?.start()
    }

    private fun stopSilence() {
        running = false
        try {
            worker?.join(500)
        } catch (e: Exception) {
        }
        try {
            track?.stop()
        } catch (e: Exception) {
        }
        track?.release()
        track = null
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(volRx)
        } catch (e: Exception) {
        }
        stopSilence()
        session?.isActive = false
        session?.release()
        session = null
        log("SERVICE stopped")
        super.onDestroy()
    }
}
