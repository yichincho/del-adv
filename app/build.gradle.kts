plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.yichincho.deladv"; compileSdk = 35
  defaultConfig { applicationId = "com.yichincho.deladv"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "0.4.0" }
  buildFeatures { buildConfig = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  lint { abortOnError = false }
}
dependencies {
  testImplementation("junit:junit:4.13.2")
  // android.jar only stubs org.json; unit tests need the real implementation.
  testImplementation("org.json:json:20240303")
}
