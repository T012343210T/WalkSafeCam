package com.example.walksafecam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.walksafecam.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var heightPercent = 25

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkOverlayPermissionThenStart()
            } else {
                Toast.makeText(this, "دسترسی دوربین لازم است", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "برای نمایش روی سایر برنامه‌ها باید اجازه دهید", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekHeight.progress = heightPercent
        binding.seekHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                heightPercent = progress.coerceAtLeast(10)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnStart.setOnClickListener {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    private fun checkOverlayPermissionThenStart() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            requestOverlayPermission.launch(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_HEIGHT_PERCENT, heightPercent)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "نوار دوربین فعال شد", Toast.LENGTH_SHORT).show()
    }
}
