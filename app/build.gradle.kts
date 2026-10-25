import java.util.Base64
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun resolveBuildValue(name: String): String =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: localProperties.getProperty(name)
        ?: ""

fun buildConfigLiteral(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

fun containsServiceRoleCredential(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.startsWith("sb_secret_", ignoreCase = true) ||
        trimmed.startsWith("service_role", ignoreCase = true)
    ) return true

    val payload = trimmed.split('.').getOrNull(1) ?: return false
    val claims = runCatching {
        String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
    }.getOrDefault("")
    return Regex("\"role\"\\s*:\\s*\"service_role\"", RegexOption.IGNORE_CASE)
        .containsMatchIn(claims)
}

val supabaseUrlValue = resolveBuildValue("SUPABASE_URL")
val supabasePublishableKeyValue = resolveBuildValue("SUPABASE_PUBLISHABLE_KEY")
if (containsServiceRoleCredential(supabasePublishableKeyValue)) {
    throw GradleException(
        "A privileged Supabase key was supplied as SUPABASE_PUBLISHABLE_KEY. " +
            "Build stopped; use the publishable/anon key."
    )
}

android {
    namespace = "com.smarttransparentmess.erp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smarttransparentmess.erp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // These values are public client configuration; privileged keys are rejected above.
        // Empty values intentionally keep an unconfigured build safe and visibly offline.
        buildConfigField("String", "SUPABASE_URL", buildConfigLiteral(supabaseUrlValue))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigLiteral(supabasePublishableKeyValue))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
