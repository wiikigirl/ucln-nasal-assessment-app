package com.example.myucln

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Home : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val btnRegister = findViewById<Button>(R.id.btn_register)
        val btnViewPatients = findViewById<Button>(R.id.btnViewPatients)

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }

        btnViewPatients.setOnClickListener {
            startActivity(Intent(this, PatientListActivity::class.java))
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true

                R.id.nav_camera -> {
                    showPatientSelectionDialog("Camera")
                    false
                }

                R.id.nav_upload -> {
                    showPatientSelectionDialog("Upload")
                    false
                }

                R.id.nav_history -> {
                    startActivity(Intent(this, PatientListActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_home
    }

    private fun showPatientSelectionDialog(targetActivity: String) {
        val database = PatientDatabase.getDatabase(this)

        CoroutineScope(Dispatchers.IO).launch {
            val patients = database.patientDao().getPatientsForExport()

            withContext(Dispatchers.Main) {
                if (patients.isEmpty()) {
                    Toast.makeText(this@Home, "Please register a patient first!", Toast.LENGTH_LONG).show()
                    return@withContext
                }

                val patientNames = patients.map { "${it.name} (ID: ${it.patientId})" }.toTypedArray()

                AlertDialog.Builder(this@Home)
                    .setTitle("Select Patient for $targetActivity")
                    .setItems(patientNames) { _, which ->
                        val selectedPatient = patients[which]
                        val intent = if (targetActivity == "Camera") {
                            Intent(this@Home, Camera::class.java)
                        } else {
                            Intent(this@Home, Upload::class.java)
                        }

                        intent.putExtra("EXTRA_PATIENT_CASE_NUMBER", selectedPatient.caseNumber)
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}