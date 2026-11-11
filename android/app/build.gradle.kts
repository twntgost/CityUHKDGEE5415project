plugins {
    // AGP 9.x applies the Kotlin Android plugin automatically; aliasing it
    // here produces "extension with name 'kotlin' already registered" because
    // ApplicationPlugin wraps it. Compose / Serialization sub-plugins are
    // loaded by Kotlin automatically, so we don't need to alias them either.
    alias(libs.plugins.android.application)
    // Compose Compiler is still required at the module level — AGP does not
    // register it on our behalf.
    alias(libs.plugins.compose.compiler)
    // Kotlin Serialization plugin generates @Serializable companions and the
    // Companion.serializer() factory used by DataStore. AGP does not auto-apply
    // it on our behalf.
    alias(libs.plugins.kotlin.serialization)
}

// Tell the Kotlin Gradle Plugin where to find JVM 17 for compilation; AGP 9
// forwards this via its built-in Kotlin plugin.
kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.example.hrrepayment"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.hrrepayment"
        minSdk = 24
        targetSdk = 37
        versionCode = 20
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * 正式发布签名（2026-10-11）。
     *
     * 之前 release 借用 debug 签名只是为了本地能打出包；分发给朋友安装需要独立
     * 的正式密钥。**这个 keystore 必须长期保存**：以后更新版本要用同一把密钥签名，
     * 否则已安装的用户无法覆盖安装，只能卸载重装。
     * （口令写在这里是为了本地项目可复现；真要上架再换成环境变量。）
     */
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/hrpaydebt-release.jks")
            storePassword = "hrpaydebt2026"
            keyAlias = "hrpaydebt"
            keyPassword = "hrpaydebt2026"
        }
    }

    buildTypes {
        release {
            // 不开 R8：这个项目大量使用 kotlinx.serialization 与 Compose，
            // 收缩规则没有专门维护过，强行开启有运行时崩溃风险。体积换稳定。
            optimization {
                enable = false
            }
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * This build runs against a fully populated offline Gradle cache, so a handful of
 * transitive pins are forced to versions that are actually present. They are all
 * newer-or-equal to what the libraries request, except `lifecycle-viewmodel-compose`
 * and `emoji2`, where only the older artifacts exist locally — verified compatible
 * by the successful build/device run recorded in the development log.
 */
configurations.configureEach {
    resolutionStrategy {
        force(
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.7",
            "androidx.emoji2:emoji2:1.3.0",
            "androidx.emoji2:emoji2-views-helper:1.3.0",
            "androidx.annotation:annotation:1.10.0",
            "androidx.core:core:1.19.0",
            "androidx.core:core-ktx:1.19.0",
        )
    }
}

dependencies {
    // Project modules
    implementation(project(":core"))

    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose (BOM-managed)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    // Kotlin libraries (Serialization available via Kotlin Android plugin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // DataStore (typed)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    // JDK desugar (java.time on API 24)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
