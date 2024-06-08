package com.andi.driver.behavior

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.andi.driver.behavior.databinding.ActivityDetectionBinding
import com.andi.driver.behavior.utils.BoundingBox
import com.andi.driver.behavior.utils.Constants.LABELS_PATH
import com.andi.driver.behavior.utils.Constants.MODEL_PATH
import com.andi.driver.behavior.utils.Detector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import java.util.Date
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DetectionActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var binding: ActivityDetectionBinding
    private var isFrontCamera = true
    private lateinit var detector: Detector
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var database: FirebaseDatabase
    private lateinit var currentUser: FirebaseUser
    private var lastDetectionData: DetectionData? = null

    private val detectionHandler = Handler()
    private val detectionRunnable = object : Runnable {
        override fun run() {
            lastDetectionData?.let { sendDataToFirebase(it) }
            detectionHandler.postDelayed(this, 20000)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val vibrationRunnable = object : Runnable {
        override fun run() {
            vibrateDevice()
            handler.postDelayed(this, 1000)
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        database = FirebaseDatabase.getInstance()

        currentUser = FirebaseAuth.getInstance().currentUser!!
        initializeDetector()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.toggleCameraButton).setOnClickListener {
            toggleCamera()
        }

        initializeMediaPlayer()
    }

    private fun initializeDetector() {
        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.audio_) // Ensure you have audio.mp3 in res/raw
        mediaPlayer?.setOnPreparedListener {
            it.isLooping = true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            processImage(imageProxy)
        }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        detector.detect(rotatedBitmap)
    }

    private fun toggleCamera() {
        isFrontCamera = !isFrontCamera
        bindCameraUseCases()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
        handler.removeCallbacks(vibrationRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun vibrateDevice() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }
    }

    private fun playAlarmSound() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    override fun onEmptyDetect() {
        binding.overlay.invalidate()
        handler.removeCallbacks(vibrationRunnable)
        stopAlarmSound()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }

            val monitoredLabels = listOf("distracted")
            var className: String
            val detectedLabels = boundingBoxes.map { it.clsName }
            if (detectedLabels.any { it in monitoredLabels }) {
                handler.post(vibrationRunnable)

                className = if (detectedLabels.contains("focused")) {
                    "Fokus"
                } else {
                    "Tidak Fokus"
                }

                playAlarmSound()

                Toast.makeText(
                    this@DetectionActivity,
                    "Perilaku terdeteksi: $className",
                    Toast.LENGTH_LONG
                ).show()

                // Generate a new key from Firebase
                val databaseRef = FirebaseDatabase.getInstance().reference
                val key = databaseRef.child("detections").push().key

                if (key != null) {
                    val detectionData = DetectionData(
                        id = key,
                        userId = currentUser.uid,
                        cls = className,
                        createdAt = Date().toString(),
                        updatedAt = Date().toString()
                    )

                    lastDetectionData = detectionData

                    createHistory(currentUser.uid, detectionData)
                } else {
                    Log.e("DetectionActivity", "Failed to generate Firebase key")
                }
            } else {
                handler.removeCallbacks(vibrationRunnable)
                stopAlarmSound()
            }
        }
    }

    private fun createHistory(userId: String, dataDetection: DetectionData) {
        // Get a reference to the "history" node for the specified user
        val historyRef = database.reference.child("history").child(userId)

        // Generate a new child location with a unique key and set the value to dataDetection
        historyRef.push().setValue(dataDetection)
            .addOnSuccessListener {
                // Handle success, if necessary
                println("Data added successfully")
            }
            .addOnFailureListener { exception ->
                // Handle failure, if necessary
                println("Failed to add data: ${exception.message}")
            }
    }


    private fun sendDataToFirebase(lastDetectionData: DetectionData) {
        createHistory(currentUser.uid, lastDetectionData)
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

