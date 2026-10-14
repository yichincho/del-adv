plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.yichincho.deladv"; compileSdk = 35
  defaultConfig { applicationId = "com.yichincho.deladv"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.3.0" }
  buildFeatures { buildConfig = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  lint { abortOnError = false }
}
dependencies { testImplementation("junit:junit:4.13.2") }
