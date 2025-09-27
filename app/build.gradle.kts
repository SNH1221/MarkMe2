plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("com.google.gms.google-services")
    kotlin("kapt")
}

android {
    namespace = "com.skyrist.markme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skyrist.markme"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        buildConfigField(""")
        buildConfigField("String", """)


        // replace YOUR_API_KEY_HERE with the key you generated (keep quotes)
        buildConfigField("String", """)

    }

    buildFeatures{
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")


    // add inside dependencies { ... }
    implementation("com.google.firebase:firebase-firestore-ktx:24.5.0")
    implementation("com.google.firebase:firebase-storage-ktx:20.2.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Fragment KTX (for androidx.fragment.app.commit { ... } and other extensions)
    implementation("androidx.fragment:fragment-ktx:1.6.1")




    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")


    // Retrofit + Gson + OkHttp logging
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

// Firebase (agar already added to project nahi to)
    implementation("com.google.firebase:firebase-firestore-ktx:24.4.4") // version may vary
    implementation("com.google.firebase:firebase-analytics-ktx:21.4.0") // optional


    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")



}
