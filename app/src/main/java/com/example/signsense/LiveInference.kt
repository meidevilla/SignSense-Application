package com.example.signsense

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.signsense.databinding.ActivityLiveInferenceBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveInference : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {
    private lateinit var binding: ActivityLiveInferenceBinding
    private val isFrontCamera = false
    private lateinit var detector: ObjectDetectorHelper
    private var bitmapBuffer: Bitmap? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var tts: TextToSpeech
    private var lastDetectedClass: String? = null
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveInferenceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.US
                tts.setSpeechRate(1.2f)
            }
        }
        if (allPermissionsGranted()) {
            setUpDetector()
            binding.viewFinder.post { setUpCamera() }
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun setUpDetector() {
        detector = ObjectDetectorHelper(
            threshold = 0.80f,
            numThreads = 4,
            maxResults = 5,
            currentDelegate = ObjectDetectorHelper.DELEGATE_GPU,
            context = this,
            detectorListener = this
        )
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val rotation = binding.viewFinder.display.rotation
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(rotation)
            .build()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    processImage(image)
                }
            }

        cameraProvider.unbindAll()
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed.", exc)
        }
    }

    private fun processImage(image: ImageProxy) {
        image.use {
            val bitmap = getBitmapFromImage(image)
            detector.detect(bitmap)
        }
    }

    private fun getBitmapFromImage(image: ImageProxy): Bitmap {
        if (bitmapBuffer == null) {
            bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        }
        bitmapBuffer?.let { bitmap ->
            bitmap.copyPixelsFromBuffer(image.planes[0].buffer)
            val matrix = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) postScale(-1f, 1f, image.width.toFloat(), image.height.toFloat())
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            setUpDetector()
            binding.viewFinder.post { setUpCamera() }
        } else {
            Log.e(TAG, "Camera permission denied.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            setUpDetector()
            binding.viewFinder.post { setUpCamera() }
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.clear()
            Log.d(TAG, "No detection results.")
        }
    }

    override fun onResults(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
            boundingBoxes.forEach { box ->
                val distance = calculateDistanceFromCamera(box)
                Log.d(TAG, "Sign Detected: ${box.clsName}: Distance = ${"%.2f".format(distance)} meters")
                speakDetectionResults(box.clsName, distance)
            }
        }
    }

    private fun calculateDistanceFromCamera(box: BoundingBox): Float {
        val focalLength = 0.266f
        val actualObjectSize = 0.9f
        val imageObjectSize = (box.w + box.h) / 2.0f
        return (focalLength * actualObjectSize) / imageObjectSize
    }

    private fun speakDetectionResults(clsName: String, distance: Float) {
        if (clsName != lastDetectedClass) {
            lastDetectedClass = clsName
            val textToSpeak = "${clsName}, ${"%.2f".format(distance)} meters."
            Log.d(TAG, "Text-To-Speech: $textToSpeak")
            speak(textToSpeak)
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(baseContext, error, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "Camera"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
