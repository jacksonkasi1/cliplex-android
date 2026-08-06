package com.learnthis.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.setPadding

class OverlayService : Service() {

 private var windowManager: WindowManager? = null
 private var overlayView: View? = null

 override fun onCreate() {
 super.onCreate()
 windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
 }

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
 when (intent?.action) {
 ACTION_SHOW_OVERLAY -> showOverlay()
 ACTION_HIDE_OVERLAY -> hideOverlay()
 }
 return START_NOT_STICKY
 }

 private fun showOverlay() {
 if (overlayView != null) return

 val composeView = ComposeView(this).apply {
 setContent {
 OverlayFabContent(
 onClick = { sendBroadcast(Intent(ACTION_CAPTURE_REQUESTED)) }
 )
 }
 }

 val params = WindowManager.LayoutParams(
 WindowManager.LayoutParams.WRAP_CONTENT,
 WindowManager.LayoutParams.WRAP_CONTENT,
 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
 WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
 else
 WindowManager.LayoutParams.TYPE_PHONE,
 WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
 WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
 PixelFormat.TRANSLUCENT
 )
 params.gravity = Gravity.END or Gravity.BOTTOM
 params.x = 32
 params.y = 32

 overlayView = composeView
 windowManager?.addView(composeView, params)
 }

 private fun hideOverlay() {
 overlayView?.let {
 windowManager?.removeView(it)
 overlayView = null
 }
 }

 override fun onBind(intent: Intent?): IBinder? = null

 override fun onDestroy() {
 hideOverlay()
 super.onDestroy()
 }

 companion object {
 const val ACTION_SHOW_OVERLAY = "com.learnthis.action.SHOW_OVERLAY"
 const val ACTION_HIDE_OVERLAY = "com.learnthis.action.HIDE_OVERLAY"
 const val ACTION_CAPTURE_REQUESTED = "com.learnthis.action.CAPTURE_REQUESTED"
 }
}

@Composable
fun OverlayFabContent(onClick: () -> Unit) {
 androidx.compose.material3.FloatingActionButton(
 onClick = onClick,
 containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
 contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
 modifier = androidx.compose.ui.Modifier.size(56.dp),
 ) {
 androidx.compose.material3.Icon(
 androidx.compose.material.icons.Icons.Default.Mic,
 contentDescription = "Capture"
 )
 }
}
