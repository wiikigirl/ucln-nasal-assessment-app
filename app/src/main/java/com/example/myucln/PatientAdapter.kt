package com.example.myucln

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PatientAdapter(
    private val patientList: List<Patient>,
    private val onDeleteClick: (Patient) -> Unit
) : RecyclerView.Adapter<PatientAdapter.PatientViewHolder>() {

    class PatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
        val tvItemPatientId: TextView = itemView.findViewById(R.id.tvItemPatientId)
        val tvItemDetails: TextView = itemView.findViewById(R.id.tvItemDetails)
        val tvDeletePatient: TextView = itemView.findViewById(R.id.tvDeletePatient)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_patient, parent, false)
        return PatientViewHolder(view)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        val currentPatient = patientList[position]

        holder.tvItemName.text = currentPatient.name
        holder.tvItemPatientId.text = "ID: ${currentPatient.patientId}"

        val fileStatus = currentPatient.imageName ?: "No image attached"

        holder.tvItemDetails.text = "Age: ${currentPatient.age} | Sex: ${currentPatient.sex}\nFile: $fileStatus"

        holder.tvDeletePatient.setOnClickListener {
            onDeleteClick(currentPatient)
        }
    }

    override fun getItemCount(): Int {
        return patientList.size
    }
}