package com.example.myucln

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.myucln.databinding.ActivityUploadBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class Upload : AppCompatActivity() {

    private lateinit var binding: ActivityUploadBinding
    private var interpreter: Interpreter? = null

    private val modelInputSize = 224
    private val modelOutputPoints = 12
    private var currentCaseNumber: Int = -1

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            binding.ivPatientImage.setImageURI(uri)
            processPatientImage(uri)
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure your XML layout file is named activity_upload.xml
        binding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCaseNumber = intent.getIntExtra("EXTRA_PATIENT_CASE_NUMBER", -1)
        if (currentCaseNumber == -1) {
            Toast.makeText(this, "Error: No patient selected.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeLiteRTModel()

        binding.btnUpload.setOnClickListener {
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun initializeLiteRTModel() {
        try {
            val modelBuffer = loadModelFile("UCLN_landmarker.tflite")
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load LiteRT model.", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun processPatientImage(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }.copy(Bitmap.Config.ARGB_8888, true)

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
            val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

            val outputArray = Array(1) { FloatArray(modelOutputPoints) }
            interpreter?.run(byteBuffer, outputArray)

            val predictedLandmarks = outputArray[0]
            binding.overlayView.updateLandmarks(predictedLandmarks)

            generateAssessmentReport(predictedLandmarks)
            saveAssessmentToDatabase(bitmap, predictedLandmarks)

        } catch (e: Exception) {
            Toast.makeText(this, "Error processing image.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * modelInputSize * modelInputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(modelInputSize * modelInputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in intValues) {
            byteBuffer.putFloat((pixel shr 16 and 0xFF).toFloat())
            byteBuffer.putFloat((pixel shr 8 and 0xFF).toFloat())
            byteBuffer.putFloat((pixel and 0xFF).toFloat())
        }
        return byteBuffer
    }

    private fun generateAssessmentReport(landmarks: FloatArray) {
        // FIXED: Removed landmarks.isNotEmpty() to prevent the "Condition is always true" compiler warning
        val hasValidLandmarks = landmarks.any { it != 0.0f }

        if (hasValidLandmarks) {
            val p1x = String.format("%.2f", landmarks[0])
            val p1y = String.format("%.2f", landmarks[1])
            binding.tvAssessmentResult.text = "Assessment Complete! Main Point: ($p1x, $p1y)"
            binding.tvAssessmentResult.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            binding.tvAssessmentResult.text = "Error: Could not assess landmarks clearly."
            binding.tvAssessmentResult.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun saveAssessmentToDatabase(originalBitmap: Bitmap, landmarks: FloatArray) {
        val database = PatientDatabase.getDatabase(this)

        CoroutineScope(Dispatchers.IO).launch {
            val patient = database.patientDao().getPatientById(currentCaseNumber) ?: return@launch

            val combinedBitmap = drawLandmarksOnBitmap(originalBitmap, landmarks)
            val savedImagePath = saveImageToPublicStorage(combinedBitmap, patient.patientId)
            val file = File(savedImagePath)

            val updatedPatient = patient.copy(
                imagePath = savedImagePath,
                imageName = file.name
            )

            database.patientDao().update(updatedPatient)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@Upload, "Saved to Images: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun drawLandmarksOnBitmap(originalBitmap: Bitmap, landmarks: FloatArray): Bitmap {
        // Create a mutable copy of the original high-res photo to draw on
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // Set up a bright, solid paint brush
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // AUTO-DETECT: Check if coordinates are normalized (0.0 to 1.0) or absolute (0.0 to 224.0)
        // We look at the highest value in the array. If it's <= 1.0, it's normalized.
        val maxVal = landmarks.maxOrNull() ?: 0f
        val isNormalized = maxVal <= 1.0f

        // Calculate the correct multipliers based on the coordinate space
        val scaleX = if (isNormalized) originalBitmap.width.toFloat() else (originalBitmap.width / 224f)
        val scaleY = if (isNormalized) originalBitmap.height.toFloat() else (originalBitmap.height / 224f)

        // Dynamically size the dots so they look proportional regardless of photo resolution
        val dotRadius = originalBitmap.width * 0.015f

        // Draw each point onto the high-res canvas
        for (i in 0 until landmarks.size step 2) {
            if (i + 1 < landmarks.size) {
                val x = landmarks[i] * scaleX
                val y = landmarks[i + 1] * scaleY

                // Safety check: only draw if the points are within the picture boundaries
                if (x in 0f..originalBitmap.width.toFloat() && y in 0f..originalBitmap.height.toFloat()) {
                    canvas.drawCircle(x, y, dotRadius, paint)
                }
            }
        }

        return resultBitmap
    }

    private fun saveImageToPublicStorage(bitmap: Bitmap, patientId: String): String {
        // Saves directly to public device storage: Internal Storage > Pictures > MyUCLN
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "MyUCLN"
        )
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val file = File(folder, "${patientId}_${System.currentTimeMillis()}.jpg")

        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fos?.close()
        }

        // Force Android's Media Store to scan the file so it shows up in the Gallery app right away
        android.media.MediaScannerConnection.scanFile(
            this,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null
        )

        return file.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter?.close()
    }
}