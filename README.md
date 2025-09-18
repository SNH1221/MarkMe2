# MarkMe 📌  
RFID-based Automated Attendance & Verification System (SIH 2025 Project)

## 🚀 Overview  
MarkMe is a smart attendance system that uses *RFID cards* to automate student attendance in classrooms. To ensure *accuracy and prevent proxy attendance, the system also includes a **photo verification step* where randomly selected students must verify their presence with photos. All data is stored securely in *Firebase Firestore* and *Firebase Storage*. Teachers can also send SMS alerts to parents and export reports to government portals.

## ✨ Features  
- 📲 Android app built in *Kotlin*  
- 🏷 RFID-based student attendance  
- 📸 Photo verification to prevent proxy attendance  
- ☁ Firebase Firestore + Storage as backend  
- 📊 Attendance records with analytics & history  
- 📤 Export attendance to Govt. portal  
- 📩 SMS alerts to parents (via Twilio/Render)  
- 🤖 Chatbot integration (Gemini API demo)  

## 🛠 Tech Stack  
- *Frontend*: Android Studio (Kotlin, XML)  
- *Backend*: Firebase Firestore, Firebase Storage, Render API  
- *APIs*: Twilio SMS, Gemini AI (chatbot demo)  

## 📂 Project Structure  
- Activities/ → TeacherDashboard, ClassAttendance, EditAttendance, SampleVerification  
- Adapters/ → RecyclerView adapters (Students, EditAttendance, Chat)  
- Fragments/ → AttendanceRecords UI  
- Network/ → API layer (ApiClient, SmsApi, SmsRepository, AttendanceSender)  
- Models/ → Data models (SessionRecord, SessionStudent, StudentItem)  
- Verification/ → SampleVerificationActivity + layouts  
- Chatbot/ → ChatbotActivity  

## ⚡ How It Works  
1. Teacher starts a new attendance session  
2. Students scan their RFID cards  
3. System marks attendance in Firestore  
4. Teacher saves session → system selects *random sample (2–8 students)* for verification  
5. Teacher uploads/captures photos of sampled students → stored in Firebase Storage  
6. Firestore updates fields like: sampleNeeded, sampleStatus, verificationStatus, photos.<studentId>  
7. SMS notification sent to parents (if enabled)  
8. Records available in dashboard + export option  

## 👨‍💻 Team  
Developed as part of *Smart India Hackathon 2025* by Team *Skyrist*.

---
