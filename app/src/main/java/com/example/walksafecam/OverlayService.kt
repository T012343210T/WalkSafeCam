package com.example.walksafecam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService

class OverlayService : LifecycleService() {

    companion object {
        const val EXTRA_HEIGHT_PERCENT = "height_percent"
        private const val CHANNEL_ID = "walk_safe_cam_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val heightPercent = intent?.getIntExtra(EXTRA_HEIGHT_PERCENT, 25) ?: 25
        if (overlayView == null) {
            addOverlayView(heightPercent)
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Walk Safe Cam", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Walk Safe Cam فعال است")
            .setContentText("نوار دوربین بالای صفحه در حال اجراست")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlayView(heightPercent: Int) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_camera, null)
        overlayView = view

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val stripHeight = (metrics.heightPixels * (heightPercent / 100f)).toInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            stripHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        windowManager.addView(view, params)

        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            stopSelf()
        }

        startCamera(view.findViewById(R.id.previewView))
    }

    private fun startCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val selector = buildWideBackCameraSelector(provider)

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Picks the back-facing camera with the shortest focal length (widest field of view),
     * which on most phones with multiple rear cameras is the ultra-wide lens.
     */
    private fun buildWideBackCameraSelector(provider: ProcessCameraProvider): CameraSelector {
        val backInfos = provider.availableCameraInfos.filter {
            it.lensFacing == CameraSelector.LENS_FACING_BACK
        }
        if (backInfos.isEmpty()) return CameraSelector.DEFAULT_BACK_CAMERA

        var widest = backInfos.first()
        var minFocal = Float.MAX_VALUE
        for (info in backInfos) {
            try {
                val camera2Info = Camera2CameraInfo.from(info)
                val focalLengths = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                )
                val focal = focalLengths?.minOrNull()
                if (focal != null && focal < minFocal) {
                    minFocal = focal
                    widest = info
                }
            } catch (e: Exception) {
                // ignore, keep default
            }
        }

        return CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { it == widest } }
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // view may already be removed
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent) = super.onBind(intent)
}
