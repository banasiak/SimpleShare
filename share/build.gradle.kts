plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.banasiak.android.simpleshare"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.banasiak.android.simpleshare"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildFeatures {
    buildConfig = true
    compose = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.majorVersion
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

composeCompiler {
  enableStrongSkippingMode = true
  reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

val ktlint: Configuration by configurations.creating

dependencies {
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation.layout.android)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.runtime.android)
  implementation(libs.androidx.compose.ui.tooling.preview.android)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.dagger.hilt.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.material)
  implementation(libs.okhttp)
  implementation(libs.okhttp.coroutines)
  implementation(libs.review.ktx)
  implementation(libs.timber)
  implementation(libs.urldetector)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.okhttp.bom))
  ksp(libs.dagger.compiler)
  ksp(libs.dagger.hilt.android.compiler)

  ktlint(libs.ktlint) {
    attributes {
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
  }
}

val ktlintCheck by tasks.registering(JavaExec::class) {
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Check Kotlin code style"
  classpath = ktlint
  mainClass.set("com.pinterest.ktlint.Main")
  // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
  args(
    "**/src/**/*.kt",
    "**.kts",
    "!**/build/**"
  )
}

tasks.check {
  dependsOn(ktlintCheck)
}

tasks.register<JavaExec>("format") {
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Check Kotlin code style and format"
  classpath = ktlint
  mainClass.set("com.pinterest.ktlint.Main")
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
  args(
    "-F",
    "**/src/**/*.kt",
    "**.kts",
    "!**/build/**"
  )
}