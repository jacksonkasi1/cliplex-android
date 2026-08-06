package com.learnthis.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView

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

 val composeView = ComposeView(this)
 composeView.setContent {
 OverlayFabContent(onClick = {
 sendBroadcast(Intent(ACTION_CAPTURE_REQUESTED))
 })
 }

 val density = resources.displayMetrics.density
 val sizePx = (56 * density).toInt()

 val params = WindowManager.LayoutParams(
 sizePx, sizePx,
 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
 WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
 else
 WindowManager.LayoutParams.TYPE_PHONE,
 WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
 PixelFormat.TRANSLUCENT
 )
 params.gravity = Gravity.END or Gravity.BOTTOM
 params.x = (16 * density).toInt()
 params.y = (16 * density).toInt()

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
 FloatingActionButton(
 onClick = onClick,
 containerColor = MaterialTheme.colorScheme.primaryContainer,
 contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
 modifier = Modifier.size(56.dp),
 ) {
 Icon(Icons.Default.Mic, contentDescription = "Capture")
 }
}
