package com.airlink.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager

/**
 * Visible, user-approved local screen-capture session.
 *
 * Captured frames are immediately closed here. No frame is saved or transmitted.
 * A later WebRTC implementation can replace the ImageReader sink with a guarded
 * encoder surface while preserving this explicit session lifecycle.
 */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var stopping = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            ACTION_START -> {
                if (projection != null) return START_NOT_STICKY
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = readResultData(intent)
                if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                try {
                    startVisibleForeground()
                    beginCapture(resultCode, resultData)
                } catch (error: Exception) {
                    stopCapture()
                    stopSelf(startId)
                }
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun startVisibleForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AirLink screen sharing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Visible status and stop control for an active screen-sharing session."
                    setShowBadge(false)
                }
            )
        }

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("AirLink screen capture is active")
            .setContentText("This device's screen is being captured locally. Tap Stop to end.")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Stop sharing",
                    stopPendingIntent
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginCapture(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val activeProjection = projectionManager.getMediaProjection(resultCode, resultData)
        projection = activeProjection
        activeProjection.registerCallback(projectionCallback, Handler(mainLooper))

        val (width, height) = captureDimensions()
        val densityDpi = resources.configuration.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        captureThread = HandlerThread("AirLinkScreenFrames").apply { start() }
        val frameHandler = Handler(captureThread!!.looper)
        reader.setOnImageAvailableListener({ source ->
            // Keep the pipeline bounded: consume and immediately release each frame.
            // WebRTC encoding/transport is deliberately not enabled in this milestone.
            source.acquireLatestImage()?.close()
        }, frameHandler)

        virtualDisplay = activeProjection.createVirtualDisplay(
            "AirLinkScreenCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            frameHandler
        ) ?: throw IllegalStateException("Android did not create the capture display.")

        sendSessionState(active = true)
    }

    private fun captureDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(it)
            }
            metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
        }
    }

    private fun stopCapture() {
        if (stopping) return
        stopping = true

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        captureThread?.quitSafely()
        captureThread = null

        projection?.let { activeProjection ->
            try {
                activeProjection.unregisterCallback(projectionCallback)
            } catch (_: Exception) {
            }
            try {
                activeProjection.stop()
            } catch (_: Exception) {
            }
        }
        projection = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        sendSessionState(active = false)
        stopSelf()
    }

    private fun sendSessionState(active: Boolean) {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_ACTIVE, active)
        )
    }

    override fun onDestroy() {
        if (!stopping) stopCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.airlink.app.action.START_SCREEN_CAPTURE"
        const val ACTION_STOP = "com.airlink.app.action.STOP_SCREEN_CAPTURE"
        const val ACTION_STATE_CHANGED = "com.airlink.app.action.SCREEN_CAPTURE_STATE"
        const val EXTRA_ACTIVE = "screen_capture_active"
        const val EXTRA_RESULT_CODE = "screen_capture_result_code"
        const val EXTRA_RESULT_DATA = "screen_capture_result_data"

        private const val CHANNEL_ID = "airlink_screen_capture"
        private const val NOTIFICATION_ID = 4102
        private const val STOP_REQUEST_CODE = 4103
    }
}
