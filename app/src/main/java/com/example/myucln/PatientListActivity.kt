package com.example.myucln

import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PatientListActivity : AppCompatActivity() {

    private lateinit var recyclerViewPatients: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_list)

        recyclerViewPatients = findViewById(R.id.recyclerViewPatients)
        recyclerViewPatients.layoutManager = LinearLayoutManager(this)

        loadPatientsFromDatabase()

        val btnExportCsv = findViewById<Button>(R.id.btnExportCsv)
        btnExportCsv.setOnClickListener {
            exportDatabaseToCSV()
        }
    }

    private fun loadPatientsFromDatabase() {
        val database = PatientDatabase.getDatabase(this)

        CoroutineScope(Dispatchers.IO).launch {
            database.patientDao().getAllPatients().collect { allPatients ->
                withContext(Dispatchers.Main) {
                    val adapter = PatientAdapter(allPatients) { patientToDelete ->
                        CoroutineScope(Dispatchers.IO).launch {
                            database.patientDao().delete(patientToDelete)
                        }
                    }
                    recyclerViewPatients.adapter = adapter
                }
            }
        }
    }

    private fun exportDatabaseToCSV() {
        val database = PatientDatabase.getDatabase(this)

        CoroutineScope(Dispatchers.IO).launch {
            val allPatients = database.patientDao().getPatientsForExport()

            var csvData = "Case Number,Patient ID,Identity Number,Name,Age,Sex,Image Path,Image Name\n"

            for (patient in allPatients) {
                val imgPath = patient.imagePath ?: "No Image"
                val imgName = patient.imageName ?: "No Assessment Yet"

                csvData += "${patient.caseNumber},${patient.patientId},${patient.identityNumber},${patient.name},${patient.age},${patient.sex},${imgPath},${imgName}\n"
            }

            try {
                val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (!folder.exists()) folder.mkdirs()

                val file = File(folder, "Patient_Export_AI.csv")
                file.writeText(csvData)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PatientListActivity, "Saved to Documents: ${file.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PatientListActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}