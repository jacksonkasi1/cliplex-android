package com.learnthis.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.learnthis.service.CaptureService
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
			ACTION_HIDE_OVERLAY -> { hideOverlay(); stopSelf() }
		}
		return START_NOT_STICKY
	}

	private fun showOverlay() {
		if (overlayView != null || !Settings.canDrawOverlays(this)) return
		val density = resources.displayMetrics.density
		val sizePx = (60 * density).toInt()
		val button = ImageButton(this).apply {
			contentDescription = "Learn This — start or finish capture"
			setImageResource(android.R.drawable.ic_btn_speak_now)
			setColorFilter(Color.WHITE)
			background = GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				setColor(Color.rgb(65, 82, 180))
			}
			elevation = 8 * density
			setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
		}
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
		button.setOnTouchListener(MovableTouchListener(button, params))
		overlayView = button
		windowManager?.addView(button, params)
	}

	private inner class MovableTouchListener(
		private val view: View,
		private val params: WindowManager.LayoutParams,
	) : View.OnTouchListener {
		private var initialX = 0
		private var initialY = 0
		private var touchX = 0f
		private var touchY = 0f
		private var moved = false

		override fun onTouch(ignored: View, event: MotionEvent): Boolean {
			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					initialX = params.x; initialY = params.y
					touchX = event.rawX; touchY = event.rawY; moved = false
					return true
				}
				MotionEvent.ACTION_MOVE -> {
					val dx = (event.rawX - touchX).toInt()
					val dy = (event.rawY - touchY).toInt()
					moved = moved || abs(dx) > 8 || abs(dy) > 8
					params.x = initialX + dx
					params.y = initialY + dy
					windowManager?.updateViewLayout(view, params)
					return true
				}
				MotionEvent.ACTION_UP -> {
					if (!moved) {
						startService(Intent(this@OverlayService, CaptureService::class.java).setAction(CaptureService.ACTION_TOGGLE))
					} else {
						val screenWidth = resources.displayMetrics.widthPixels
						params.x = if (params.x + view.width / 2 < screenWidth / 2) 0 else screenWidth - view.width
						windowManager?.updateViewLayout(view, params)
					}
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
		const val ACTION_SHOW_OVERLAY = "com.learnthis.action.SHOW_OVERLAY"
		const val ACTION_HIDE_OVERLAY = "com.learnthis.action.HIDE_OVERLAY"
	}
}
