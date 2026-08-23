import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
}

val secrets = Properties()
val secretsFile = rootProject.file("mqtt-secrets.properties")
if (secretsFile.exists()) {
    secretsFile.inputStream().use { secrets.load(it) }
}

fun secretOrEmpty(key: String): String {
    val raw = secrets.getProperty(key, "") ?: ""
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.ambulance.driver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ambulance.driver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "MQTT_HOST", "\"${secretOrEmpty("mqtt.host")}\"")
        buildConfigField("int", "MQTT_PORT", secrets.getProperty("mqtt.port", "8883"))
        buildConfigField("String", "MQTT_USERNAME", "\"${secretOrEmpty("mqtt.username")}\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"${secretOrEmpty("mqtt.password")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
}
