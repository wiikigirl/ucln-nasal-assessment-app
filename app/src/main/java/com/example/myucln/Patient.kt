package com.example.myucln

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patient_table")
data class Patient(
    @PrimaryKey(autoGenerate = true)
    val caseNumber: Int = 0,

    val identityNumber: String,
    val patientId: String,
    val name: String,
    val age: String,
    val sex: String,
    val registrationDate: Long = System.currentTimeMillis(),

    // The exact folder path on the device
    val imagePath: String? = null,

    // The clean file name (e.g., "PT123_170000.jpg")
    val imageName: String? = null
)