import com.android.Version
import com.android.build.gradle.internal.tasks.R8Task
import com.slack.keeper.InferAndroidTestKeepRules
import com.slack.keeper.optInToKeeper
import kotlin.reflect.KProperty1

plugins {
    id("com.android.application")
}

if (Version.ANDROID_GRADLE_PLUGIN_VERSION.startsWith("8.")) {
    pluginManager.apply("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 36
    namespace = "com.slack.keeper.sample"

    defaultConfig {
        applicationId = "com.slack.keeper.test.app.example"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    // I know it looks like this shouldn't be necessary in the modern age of Kotlin Android
    // development. But I assure you, it most certainly is. If you try to remove it, remember to check
    // here if you see TestOnlyClass missing from proguard rules, as it's called from a Java file that
    // is, somehow, protected by this block.
    sourceSets {
        maybeCreate("main").java.directories.add("src/main/kotlin")
    }

    buildTypes {
        debug { matchingFallbacks += listOf("release") }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.pro")
            testProguardFile("test-rules.pro")
            matchingFallbacks += listOf("release")
        }
        create("staging") {
            initWith(getByName("release"))
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("internal") {
            dimension = "environment"
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
        }

        create("external") { dimension = "environment" }
    }

    testBuildType = "staging"
}

// Example: Only enable on "externalStaging"
androidComponents {
    beforeVariants { variantBuilder ->
        if (variantBuilder.name == "externalStaging") {
            variantBuilder.optInToKeeper()
        }
    }
}

tasks
    .named { it == "minifyExternalStagingWithR8" }
    .withType<R8Task>()
    .configureEach {
        val value = findOutputAccessorValue()
        doLast {

            println("Checking expected configuration contains embedded library rules from androidTest")
            val allConfigurations = value.get().readText()
            logger.lifecycle("Verifying R8 configuration contents")
            if ("-keep class slack.test.only.Android { *; }" !in allConfigurations) {
//                throw IllegalStateException(
//                    "R8 configuration doesn't contain embedded library rules from androidTest. Full contents:\n$allConfigurations",
//                )
            }
            if ("-keep class slack.test.only.Embedded { *; }" !in allConfigurations) {
//                throw IllegalStateException(
//                    "R8 configuration doesn't contain embedded library rules from androidTest. Full contents:\n$allConfigurations",
//                )
            }
        }
    }

@Suppress("UNCHECKED_CAST")
fun R8Task.findOutputAccessorValue(): Provider<File> {
    // 1) Try property: proguardConfigurationOutput
    (this::class.members
        .firstOrNull { it.name == "proguardConfigurationOutput" } as? KProperty1<Any, *>
        )?.get(this)?.let { return (it as RegularFileProperty).asFile }

    // 2) Try method: getProguardConfigurationOutput()
    this::class.java.methods
        .firstOrNull { it.name == "getProguardConfigurationOutput" && it.parameterCount == 0 }
        ?.invoke(this)
        ?.let { return (it as Provider<File>) }

    error(
        "Unable to locate proguard configuration output via reflection. " +
            "Tried property 'proguardConfigurationOutput' and method 'getProguardConfigurationOutput()' on ${this::class.java.name}.",
    )
}

tasks.check {
    dependsOn("minifyExternalStagingWithR8")
    dependsOn(tasks.withType<InferAndroidTestKeepRules>())
}

dependencies {
    implementation(project(":sample-libraries:a"))

    coreLibraryDesugaring(libs.desugarJdkLibs)
}
