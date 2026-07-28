# Mobile Application for Unilateral Cleft Lip Nasal Assessment

An Android-based, offline-first mobile healthcare application designed to perform automated landmark regression and objective asymmetry assessment for Unilateral Cleft Lip and Nose (UCLN) deformities using deep learning.

Developed as a Final Year Project for the Degree of Bachelor of Computer and Communication Systems Engineering with Honours at **Universiti Putra Malaysia (UPM)** in collaboration with **Hospital Sultan Abdul Aziz Shah (HSAAS)**.

---

## Key Features

- **Automated Landmark Localization:** Regresses 6 clinically relevant nasolabial landmarks (12 coordinate outputs) across the alar base, columella, and nasal sill.
- **On-Device Deep Learning:** Powered by a transfer-learned **ResNet-50** backbone (pre-trained on VGGFace2) converted to TensorFlow Lite for 100% offline, privacy-preserving execution.
- **Real-Time Camera & Upload Support:** Leverages Android CameraX for live landmark tracking and supports local image selection.
- **Local Clinical Record Management:** Built-in patient profile registration, assessment history, local SQLite/Room database storage, and CSV data export.

---

## System Architecture & Model Performance

- **Backbone Model:** ResNet-50 (VGGFace2 Transfer Learning)
- **Model Output:** TensorFlow Lite (`.tflite`)
- **Evaluation Metrics:**
  - Testing MAE: `0.0573`
  - Testing MSE: `0.0048`

---

## Repository Structure

```text
├── app/                  # Native Kotlin Android Studio project source code
├── ml-model/             # Python training scripts, data augmentation, & TFLite conversion (Optional)
├── docs/                 # FYP documentation & architectural diagrams (Optional)
├── .gitignore            # Git configuration rules
└── README.md             # Project documentation
```
