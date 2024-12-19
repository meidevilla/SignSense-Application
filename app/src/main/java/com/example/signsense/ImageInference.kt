package com.example.signsense

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

@Suppress("PrivatePropertyName")
class ImageInference : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {

    private lateinit var imageView: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var detectionResults: TextView
    private lateinit var contentResults: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var detector: ObjectDetectorHelper
    private val PICK_IMAGE_REQUEST = 1

    private val signDescriptions = mapOf(
        "100kph Maximum Speed Limit" to "A 100kph Maximum Speed Limit is a regulatory sign that indicates that the maximum allowable speed on that road or section of road is 100 kilometers per hour.",
        "20T" to "A \"20t\" sign is a regulatory sign that indicates a maximum weight limit for vehicles, typically in metric tons. Vehicles weighing more than 20 tonnes are not allowed.",
        "4.27m Height Limit" to "A 4.27m Height Limit is a warning sign indicating the maximum headroom under a bridge or overhead obstruction.",
        "40kph Maximum Speed Limit" to "A 40kph Maximum Speed Limit is a regulatory sign indicating the maximum allowable speed is 40 kilometers per hour.",
        "50kph Maximum Speed Limit" to "A 50kph Maximum Speed Limit is a regulatory sign indicating the maximum allowable speed is 50 kilometers per hour.",
        "60kph Minimum Speed Limit" to "A 60kph Minimum Speed Limit is a regulatory sign indicating vehicles must travel at least 60 kilometers per hour.",
        "Approach to Intersection" to "Approach to Intersection: Merging Traffic is a warning sign indicating merging vehicles at an intersection.",
        "Divided Road Ahead" to "A Divided Road Ahead warns that the road will split into separate roadways or lanes divided by a barrier.",
        "Give Way" to "A Give Way sign instructs drivers to yield to vehicles on the intersecting road.",
        "Half-Y Junction" to "A Half-Y Junction is a warning sign indicating a Y-shaped intersection.",
        "Merging Traffic" to "A Merging Traffic sign warns drivers to be prepared to merge with vehicles on a separate road.",
        "No Overtaking" to "A No Overtaking sign prohibits passing other vehicles on that stretch of road.",
        "No Parking" to "A No Parking sign indicates areas where parking is prohibited.",
        "No U-Turn" to "A No U-Turn sign indicates that U-turns are prohibited at that location.",
        "Pass Either Side" to "A Pass Either Side sign indicates vehicles may pass either side of an obstacle.",
        "Road Narrows - Left" to "A Road Narrows - Left sign warns that the road ahead will narrow on the left side.",
        "Turn Right Ahead" to "A Turn Right Ahead sign indicates a sharp right turn ahead."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_inference)
        initializeViews()
        setupTextToSpeech()
        setupObjectDetector()
        imageView.setOnClickListener { openImagePicker() }
    }

    private fun initializeViews() {
        imageView = findViewById(R.id.imageView)
        overlayView = findViewById(R.id.overlayView)
        detectionResults = findViewById(R.id.detectionResults)
        contentResults = findViewById(R.id.contentResults)
    }

    private fun setupTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.US
            }
        }
    }

    private fun setupObjectDetector() {
        detector = ObjectDetectorHelper(
            threshold = 0.80f,
            numThreads = 4,
            maxResults = 5,
            currentDelegate = ObjectDetectorHelper.DELEGATE_CPU,
            context = this,
            detectorListener = this
        )
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val bitmap = getBitmapFromUri(uri)
                imageView.setImageBitmap(bitmap)
                detector.detect(bitmap)
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
            return BitmapFactory.decodeFileDescriptor(parcelFileDescriptor.fileDescriptor)
        } ?: throw IllegalArgumentException("Invalid URI")
    }

    @SuppressLint("SetTextI18n")
    override fun onEmptyDetect() {
        runOnUiThread { detectionResults.text = "No Sign Detected" }
    }

    override fun onResults(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            detectionResults.text = boundingBoxes.joinToString("\n") { "${it.clsName}: ${"%.2f".format(it.cnf * 100)}%" }
            contentResults.text = boundingBoxes.joinToString("\n") { "${it.clsName}: ${signDescriptions[it.clsName] ?: "No description available."}" }
            overlayView.setResults(boundingBoxes)
            boundingBoxes.forEach { box ->
                val textToSpeak = "${box.clsName} detected! ${signDescriptions[box.clsName] ?: "No description available."}"
                tts.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, null)
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
        tts.stop()
        tts.shutdown()
    }
}
