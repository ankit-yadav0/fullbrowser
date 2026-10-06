package com.example.fullbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Site khuli hone tak chalta hai. Notification dikhata hai taaki Android app ko background me band na kare,
 * aur jab audio chal raha ho tabhi CPU wake-lock leta hai (battery bachane ke liye).
 */
class PlaybackService : Service() {
    private var wl: PowerManager.WakeLock? = null
    private val h = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val w = wl ?: return
            val playing = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
            if (playing && !w.isHeld) w.acquire()
            else if (!playing && w.isHeld) w.release()
            h.postDelayed(this, 5000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("bg", "Background playback", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "bg")
            .setContentTitle("Full Browser")
            .setContentText("Background playback on - tap karke app kholo")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (wl == null) {
            wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fullbrowser:play")
            h.post(tick)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        wl?.takeIf { it.isHeld }?.release()
        wl = null
        super.onDestroy()
    }
}
