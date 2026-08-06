package com.learnthis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Stub for Phase 01. Full implementation in Phase 11+.
class LearningForegroundService : Service() {
 companion object {
 const val CHANNEL_ID = "learn_this_channel"
 const val NOTIFICATION_ID = 1
 }

 override fun onBind(intent: Intent?): IBinder? = null

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
 createNotificationChannel()
 val notification = NotificationCompat.Builder(this, CHANNEL_ID)
 .setContentTitle("Learn This")
 .setContentText("Learning Mode")
 .setSmallIcon(android.R.drawable.ic_btn_speak_now)
 .build()
 startForeground(NOTIFICATION_ID, notification)
 return START_NOT_STICKY
 }

 private fun createNotificationChannel() {
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
 val channel = NotificationChannel(
 CHANNEL_ID,
 "Learn This",
 NotificationManager.IMPORTANCE_LOW
 )
 val manager = getSystemService(NotificationManager::class.java)
 manager.createNotificationChannel(channel)
 }
 }

 override fun onDestroy() {
 super.onDestroy()
 }
}
