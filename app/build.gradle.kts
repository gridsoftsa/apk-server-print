plugins {
    id("com.android.application")
}

android {
    namespace = "com.gridpos.puenteimpresora"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gridpos.puenteimpresora"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // NanoHTTPD para el servidor HTTP
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // 🎯 SDK 3nStar para impresoras térmicas profesionales
    implementation(files("libs/printer-lib-2.2.4.aar"))
    
    // 📱 Para generar códigos QR (mantenemos para compatibilidad)
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    // Para debugging
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}