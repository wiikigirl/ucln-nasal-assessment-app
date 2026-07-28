package com.example.myucln

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Camera : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var ivPatientImage: ImageView
    private lateinit var ivFaceOutline: ImageView // Face guide
    private lateinit var overlayView: LandmarkOverlayView
    private lateinit var tvAssessmentResult: TextView
    private lateinit var btnSnapPhoto: Button

    private var imageCapture: ImageCapture? = null
    private var interpreter: Interpreter? = null

    private var isAssessmentLocked = false
    private lateinit var analysisExecutor: ExecutorService

    private val modelInputSize = 224
    private val modelOutputPoints = 12

    private var currentCaseNumber: Int = -1

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        currentCaseNumber = intent.getIntExtra("EXTRA_PATIENT_CASE_NUMBER", -1)
        if (currentCaseNumber == -1) {
            Toast.makeText(this, "Error: No patient selected.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        viewFinder = findViewById(R.id.cameraPreview)
        ivPatientImage = findViewById(R.id.ivPatientImage)
        ivFaceOutline = findViewById(R.id.ivFaceOutline)
        overlayView = findViewById(R.id.overlayView)
        tvAssessmentResult = findViewById(R.id.tvAssessmentResult)
        btnSnapPhoto = findViewById(R.id.btnSnapPhoto)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        analysisExecutor = Executors.newSingleThreadExecutor()

        btnBack.setOnClickListener { finish() }

        btnSnapPhoto.setOnClickListener {
            if (!isAssessmentLocked) {
                isAssessmentLocked = true
                takePhoto()
            }
        }

        initializeLiteRTModel()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                if (isAssessmentLocked) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val bitmapFrame = imageProxy.toBitmap()
                if (bitmapFrame != null) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val correctedFrame = Bitmap.createBitmap(
                        bitmapFrame, 0, 0, bitmapFrame.width, bitmapFrame.height, matrix, true
                    )
                    runLiveInference(correctedFrame)
                }
                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis)
            } catch(exc: Exception) {
                Log.e("CameraX", "Failed to lock use cases", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun runLiveInference(frameBitmap: Bitmap) {
        try {
            val scaledBitmap = Bitmap.createScaledBitmap(frameBitmap, modelInputSize, modelInputSize, true)
            val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

            val outputArray = Array(1) { FloatArray(modelOutputPoints) }
            interpreter?.run(byteBuffer, outputArray)

            val predictedLandmarks = outputArray[0]

            runOnUiThread {
                if (!isAssessmentLocked) {
                    overlayView.updateLandmarks(predictedLandmarks)

                    val hasValidLandmarks = predictedLandmarks.any { it != 0.0f }
                    if (hasValidLandmarks) {
                        tvAssessmentResult.text = "Tracking Face Features Automatically..."
                        tvAssessmentResult.setTextColor(getColor(android.R.color.holo_green_light))
                    } else {
                        tvAssessmentResult.text = "Scanning for facial features..."
                        tvAssessmentResult.setTextColor(getColor(android.R.color.white))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(externalCacheDir, "cleft_assessment_temp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        tvAssessmentResult.text = "Finalizing Assessment Report..."

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                    isAssessmentLocked = false
                    tvAssessmentResult.text = "Capture failed. Tap to try again."
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "Processing Snapshot...", Toast.LENGTH_SHORT).show()
                    processFinalCapturedPhoto(photoFile)
                }
            }
        )
    }

    private fun processFinalCapturedPhoto(file: File) {
        try {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
            val matrix = Matrix().apply { postRotate(90f) }
            val finalFreezeBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
            )

            ivPatientImage.setImageBitmap(finalFreezeBitmap)
            ivPatientImage.visibility = View.VISIBLE

            viewFinder.visibility = View.GONE
            ivFaceOutline.visibility = View.GONE

            val scaledBitmap = Bitmap.createScaledBitmap(finalFreezeBitmap, modelInputSize, modelInputSize, true)
            val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

            val outputArray = Array(1) { FloatArray(modelOutputPoints) }
            interpreter?.run(byteBuffer, outputArray)

            val finalLandmarks = outputArray[0]
            overlayView.updateLandmarks(finalLandmarks)
            generateAssessmentReport(finalLandmarks)

            saveAssessmentToDatabase(finalFreezeBitmap, finalLandmarks)

        } catch (e: Exception) {
            e.printStackTrace()
            isAssessmentLocked = false
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
        val hasValidLandmarks = landmarks.any { it != 0.0f }

        if (hasValidLandmarks) {
            val p1x = String.format("%.2f", landmarks[0])
            val p1y = String.format("%.2f", landmarks[1])
            tvAssessmentResult.text = "Assessment Complete! Main Point: ($p1x, $p1y)"
            tvAssessmentResult.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvAssessmentResult.text = "Assessment Failure: Landmarks indistinct."
            tvAssessmentResult.setTextColor(getColor(android.R.color.holo_red_dark))
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
                Toast.makeText(this@Camera, "Saved to Images: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun drawLandmarksOnBitmap(originalBitmap: Bitmap, landmarks: FloatArray): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val paint = Paint().apply {
            color = Color.GREEN // CHANGED: Changed from Color.RED to match your app's green UI!
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val maxVal = landmarks.maxOrNull() ?: 0f
        val isNormalized = maxVal <= 1.0f

        val scaleX = if (isNormalized) originalBitmap.width.toFloat() else (originalBitmap.width / 224f)
        val scaleY = if (isNormalized) originalBitmap.height.toFloat() else (originalBitmap.height / 224f)

        val dotRadius = originalBitmap.width * 0.015f

        for (i in 0 until landmarks.size step 2) {
            if (i + 1 < landmarks.size) {
                val x = landmarks[i] * scaleX
                val y = landmarks[i + 1] * scaleY

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
        analysisExecutor.shutdown()
    }
}