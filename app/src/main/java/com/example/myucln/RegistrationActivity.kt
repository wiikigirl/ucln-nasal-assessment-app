package com.example.myucln

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val etPatientId = findViewById<EditText>(R.id.etPatientId)
        val etIdentityNumber = findViewById<EditText>(R.id.etIdentityNumber)
        val etName = findViewById<EditText>(R.id.etName)
        val etAge = findViewById<EditText>(R.id.etAge)
        val rgSex = findViewById<RadioGroup>(R.id.rgSex)
        val btnSavePatient = findViewById<Button>(R.id.btnSavePatient)

        btnSavePatient.setOnClickListener {
            val patientId = etPatientId.text.toString().trim()
            val identityNumber = etIdentityNumber.text.toString().trim()
            val name = etName.text.toString().trim()
            val age = etAge.text.toString().trim()

            val selectedSexId = rgSex.checkedRadioButtonId
            val sex = if (selectedSexId == R.id.rbMale) "Male" else if (selectedSexId == R.id.rbFemale) "Female" else ""

            if (patientId.isEmpty() || identityNumber.isEmpty() || name.isEmpty() || age.isEmpty() || sex.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPatient = Patient(
                patientId = patientId,
                identityNumber = identityNumber,
                name = name,
                age = age,
                sex = sex
            )

            val database = PatientDatabase.getDatabase(this)

            CoroutineScope(Dispatchers.IO).launch {
                database.patientDao().insert(newPatient)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegistrationActivity, "Patient Saved Successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}