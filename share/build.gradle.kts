import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.kover)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.banasiak.android.simpleshare"
  compileSdk = 37
  compileSdkMinor = 1
  defaultConfig {
    applicationId = "com.banasiak.android.simpleshare"
    minSdk = 24
    targetSdk = 37
    versionCode = 9
    versionName = "NINE"
    // store/scripts/side-copy.init.gradle sets this false by name, so the store screenshots show the
    // app's own colors rather than the test phone's wallpaper
    buildConfigField("boolean", "DYNAMIC_COLOR", "true")
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
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

composeCompiler {
  reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

kover {
  reports {
    filters {
      excludes {
        // Hilt/Dagger and the Compose compiler emit these; nobody wrote them and nobody can test them
        annotatedBy("dagger.internal.DaggerGenerated")
        classes(
          "*.BuildConfig",
          "*.Hilt_*",
          "*_Factory*",
          "*_MembersInjector",
          "*_HiltModules*",
          "*ComposableSingletons*",
          "*Factory\$InstanceHolder"
        )
        packages("hilt_aggregated_deps", "dagger.hilt.internal")

        // the UI layer is out of scope while there are no Compose tests. Drop these two
        // exclusions the day androidTest exists, or the report will keep flattering the UI.
        annotatedBy("androidx.compose.runtime.Composable")
        packages("com.banasiak.android.simpleshare.ui.theme")
      }
    }
  }
}

val ktlint: Configuration = configurations.create("ktlint")

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
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.dagger.hilt.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.material)
  implementation(libs.okhttp)
  implementation(libs.okhttp.coroutines)
  implementation(libs.review.ktx)
  implementation(libs.timber)
  implementation(libs.urldetector)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.okhttp.bom))
  ksp(libs.dagger.hilt.android.compiler)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kluent.android)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk.agent)
  testImplementation(libs.mockk.android)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.turbine)
  testRuntimeOnly(libs.junit.platform.launcher)

  ktlint(libs.ktlint) {
    attributes {
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
  }
}

// the files themselves, never a glob: given a glob, ktlint walks the whole module, build/ included,
// and fails when another task deletes a file there partway through
val ktlintFiles =
  files(
    fileTree("src") { include("**/*.kt") },
    "build.gradle.kts",
    rootProject.file("build.gradle.kts"),
    rootProject.file("settings.gradle.kts")
  ).let { files -> CommandLineArgumentProvider { files.map { it.path } } }

val ktlintCheck =
  tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
    argumentProviders.add(ktlintFiles)
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
  args("-F")
  argumentProviders.add(ktlintFiles)
}