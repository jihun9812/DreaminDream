@file:Suppress("DSL_SCOPE_VIOLATION")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services") // Firebase
    id("com.google.firebase.crashlytics") // Crashlytics
    id("kotlin-parcelize")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val openaiKey = localProperties.getProperty("openai.api.key")
    ?: throw IllegalStateException("Missing 'openai.api.key' in local.properties")

android {
    namespace =  "com.dreamindream.app"
    compileSdk = 35

    defaultConfig {
        applicationId =  "com.dreamindream.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 16
        versionName = "1.07"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENAI_API_KEY", "\"$openaiKey\"")
    }


    signingConfigs {
        create("release") {
            val ksFilePath = project.findProperty("MYAPP_STORE_FILE")?.toString()
            val ksStorePass = project.findProperty("MYAPP_STORE_PASSWORD")?.toString()
            val ksAlias     = project.findProperty("MYAPP_KEY_ALIAS")?.toString()
            val ksKeyPass   = project.findProperty("MYAPP_KEY_PASSWORD")?.toString()

            if (ksFilePath.isNullOrBlank() || ksStorePass.isNullOrBlank()
                || ksAlias.isNullOrBlank() || ksKeyPass.isNullOrBlank()
            ) {
                throw GradleException(
                    "Missing keystore props. Add to gradle.properties: " +
                            "MYAPP_STORE_FILE, MYAPP_STORE_PASSWORD, MYAPP_KEY_ALIAS, MYAPP_KEY_PASSWORD"
                )
            }

            storeFile = file(ksFilePath)
            storePassword = ksStorePass
            keyAlias = ksAlias
            keyPassword = ksKeyPass
        }
    }

    buildTypes {
        release {
            // 필요하면 true로 바꿔서 난독화/리소스 축소 사용
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 디버그 서명은 기본 debug keystore 사용
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config-ktx:21.6.0")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    //Billing
    implementation("com.android.billingclient:billing-ktx:6.2.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    // ML Kit
    implementation("com.google.mlkit:translate:17.0.3")

    debugImplementation ("androidx.fragment:fragment-testing:1.6.2")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.1.0")

    // Lottie / Ads / Calendar / 기타
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.google.android.gms:play-services-ads:23.4.0")
    implementation("com.kizitonwose.calendar:view:2.5.0")

    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")



    // Unit Testing (JUnit 4)
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")


    //robel
    testImplementation("org.robolectric:robolectric:4.10.3")
    // Truth (Google의 assertion 라이브러리)
    testImplementation("com.google.truth:truth:1.1.5")

    // Robolectric (Android 컴포넌트 테스트용)
    testImplementation("org.robolectric:robolectric:4.11.1")

    // Fragment Testing
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")

    // Android Instrumentation Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")




    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
