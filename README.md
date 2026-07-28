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

---

## Getting Started

### Prerequisites

**For the Android Application:**

- Android Studio (Ladybug or newer recommended)
- Android Device / Emulator running API Level 24 (Android 7.0) or higher

**For Machine Learning Scripts:**

- Google Colab or Python 3.8+ (with TensorFlow, OpenCV, and Jupyter Notebook)

---

### Installation & Setup

**1. Clone this repository:**

git clone [https://github.com/wiikigirl/ucln-nasal-assessment-app.git](https://github.com/wiikigirl/ucln-nasal-assessment-app.git)

**2. Download the Pre-trained TFLite Model:**

- Navigate to the Releases section of this GitHub repository.
- Download the model file (.tflite).
- Move the downloaded .tflite file into the app's assets folder: app/src/main/assets/

**3. Build & Run the App:**

- Open the project directory in Android Studio.
- Let Gradle finish syncing project dependencies.
- Connect your physical Android device via USB debugging (or open an Emulator).
- Click Run (Shift + F10).

---

### Running Machine Learning Notebooks

If you want to inspect or re-train the landmark regression model:

1. Navigate to the ml-model/ folder inside this repository.
2. Upload the desired .ipynb notebook file to Google Colab.
3. Run the notebook cells sequentially to execute data preprocessing, ResNet-50 training, or TFLite conversion.

---

## Dataset & Privacy Notice

The dataset used to train and evaluate this model consists of clinical 2D frontal photographs provided under ethical approval by Hospital Sultan Abdul Aziz Shah (HSAAS).

Due to strict medical privacy regulations, patient confidentiality, and ethical compliance rules, the raw clinical image dataset is private and excluded from this public repository.

---

## Author & Acknowledgments

- **Author:** Chan Suet Jin (Department of Computer and Communication Systems Engineering, Universiti Putra Malaysia)
- **Academic Supervisor:** Dr. Muhammad Shaufil Adha Bin Shawkany Hazim
- **Clinical Advisor / Collaborator:** Dr. Pauline Yap (Plastic Surgeon, Hospital Sultan Abdul Aziz Shah - HSAAS)

---
