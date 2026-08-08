package com.jacksonkasi.cliplex.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import com.jacksonkasi.cliplex.MainActivity
import com.jacksonkasi.cliplex.service.CaptureService
import kotlin.math.abs

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
		}
		return START_NOT_STICKY
	}

	private fun showOverlay() {
		if (overlayView != null) {
			updateAppearance(overlayView as ImageButton)
			CaptureService.reportOverlayVisible()
			return
		}
		if (!Settings.canDrawOverlays(this)) return
		val density = resources.displayMetrics.density
		val sizePx = (60 * density).toInt()
		val button = AccessibleImageButton(this).apply {
			contentDescription = "ClipLex — start or finish capture"
			setColorFilter(Color.WHITE)
			elevation = 8 * density
			setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
			setOnClickListener {
				if (CaptureService.captureState.value == CaptureService.CaptureState.Capturing) {
					// The overlay tap is a direct user action, so launch the lesson Activity directly.
					// MainActivity asks the service to finalize before it renders the captured lesson.
					startActivity(Intent(this@OverlayService, MainActivity::class.java).apply {
						action = MainActivity.ACTION_FINISH_CAPTURE_AND_OPEN
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
					})
				} else {
					startService(Intent(this@OverlayService, CaptureService::class.java).setAction(CaptureService.ACTION_BEGIN))
				}
			}
		}
		updateAppearance(button)
		val params = WindowManager.LayoutParams(
			sizePx, sizePx,
			WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
			PixelFormat.TRANSLUCENT,
		).apply {
			gravity = Gravity.TOP or Gravity.START
			x = resources.displayMetrics.widthPixels - sizePx - (16 * density).toInt()
			y = resources.displayMetrics.heightPixels / 2
		}
		button.setOnTouchListener(MovableTouchListener(button, params) {
			startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_DISMISS_OVERLAY))
			hideOverlay()
			stopSelf()
		})
		try {
			windowManager?.addView(button, params)
			overlayView = button
			CaptureService.reportOverlayVisible()
			Log.i("OverlayService", "Floating control displayed")
		} catch (error: Exception) {
			overlayView = null
			CaptureService.reportOverlayError(error.message ?: error.javaClass.simpleName)
			Log.e("OverlayService", "Could not display floating control", error)
		}
	}

	private fun updateAppearance(button: ImageButton) {
		val capturing = CaptureService.captureState.value == CaptureService.CaptureState.Capturing
		button.setImageResource(if (capturing) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now)
		button.contentDescription = if (capturing) "Finish ClipLex capture" else "Start ClipLex capture"
		button.background = GradientDrawable().apply {
			shape = GradientDrawable.OVAL
			setColor(if (capturing) Color.rgb(210, 52, 65) else Color.rgb(8, 154, 82))
		}
	}

	private inner class MovableTouchListener(
		private val view: View,
		private val params: WindowManager.LayoutParams,
		private val onLongPress: () -> Unit,
	) : View.OnTouchListener {
		private val handler = Handler(Looper.getMainLooper())
		private var initialX = 0
		private var initialY = 0
		private var touchX = 0f
		private var touchY = 0f
		private var moved = false
		private var longPressed = false
		private val longPressAction = Runnable {
			if (!moved) {
				longPressed = true
				view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
				onLongPress()
			}
		}

		override fun onTouch(ignored: View, event: MotionEvent): Boolean {
			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					initialX = params.x; initialY = params.y
					touchX = event.rawX; touchY = event.rawY; moved = false
					longPressed = false
					handler.postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout().toLong())
					return true
				}
				MotionEvent.ACTION_MOVE -> {
					val dx = (event.rawX - touchX).toInt()
					val dy = (event.rawY - touchY).toInt()
					moved = moved || abs(dx) > 8 || abs(dy) > 8
					if (moved) handler.removeCallbacks(longPressAction)
					params.x = initialX + dx
					params.y = initialY + dy
					windowManager?.updateViewLayout(view, params)
					return true
				}
				MotionEvent.ACTION_UP -> {
					handler.removeCallbacks(longPressAction)
					if (longPressed) return true
					if (!moved) {
						ignored.performClick()
					} else {
						val screenWidth = resources.displayMetrics.widthPixels
						params.x = if (params.x + view.width / 2 < screenWidth / 2) 0 else screenWidth - view.width
						windowManager?.updateViewLayout(view, params)
					}
					return true
				}
				MotionEvent.ACTION_CANCEL -> {
					handler.removeCallbacks(longPressAction)
					return true
				}
			}
			return false
		}
	}

	private fun hideOverlay() {
		overlayView?.let { view ->
			try { windowManager?.removeView(view) } catch (_: Exception) { }
		}
		overlayView = null
	}

	override fun onBind(intent: Intent?): IBinder? = null
	override fun onDestroy() { hideOverlay(); super.onDestroy() }

	companion object {
		const val ACTION_SHOW_OVERLAY = "com.jacksonkasi.cliplex.action.SHOW_OVERLAY"
	}

	// The overlay is constructed directly by a Service and has no AppCompat theme dependency.
	@SuppressLint("AppCompatCustomView")
	private class AccessibleImageButton(context: Context) : ImageButton(context) {
		override fun performClick(): Boolean = super.performClick()
	}
}
