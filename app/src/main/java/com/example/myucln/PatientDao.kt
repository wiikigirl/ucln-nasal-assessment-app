package com.example.myucln

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    // Removed "suspend" to bypass the KSP Continuation bug
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(patient: Patient): Long

    @Update
    fun update(patient: Patient): Int

    @Query("SELECT * FROM patient_table WHERE caseNumber = :caseId LIMIT 1")
    fun getPatientById(caseId: Int): Patient?

    // Flow is naturally asynchronous, so it never needs suspend anyway
    @Query("SELECT * FROM patient_table ORDER BY caseNumber DESC")
    fun getAllPatients(): Flow<List<Patient>>

    @Query("SELECT * FROM patient_table ORDER BY caseNumber DESC")
    fun getPatientsForExport(): List<Patient>

    @Delete
    fun delete(patient: Patient): Int
}