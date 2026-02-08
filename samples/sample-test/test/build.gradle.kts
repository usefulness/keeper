import com.android.Version
import com.android.build.gradle.internal.tasks.R8Task
import com.google.common.truth.Truth.assertThat
import com.slack.keeper.InferAndroidTestKeepRules
import com.slack.keeper.optInToKeeper
import kotlin.reflect.KProperty1

buildscript {
    dependencies {
        // Truth has nice string comparison APIs and error messages
        classpath(libs.truth)
    }
}

plugins {
    id("com.android.test")
    id("io.github.usefulness.keeper")
}

if (Version.ANDROID_GRADLE_PLUGIN_VERSION.startsWith("8.")) {
    pluginManager.apply("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 36
    namespace = "com.slack.keeper.test.sample"
    targetProjectPath = ":sample-test:app"

    defaultConfig {
        minSdk = 21
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // I know it looks like this shouldn't be necessary in the modern age of Kotlin Android
    // development. But I assure you, it most certainly is. If you try to remove it, remember to check
    // here if you see TestOnlyClass missing from proguard rules, as it's called from a Java file that
    // is, somehow, protected by this block.
    sourceSets {
        maybeCreate("main").java.directories.add("src/main/kotlin")
    }

    buildTypes {
        named("debug") { matchingFallbacks += listOf("release") }
        register("staging") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            testProguardFile("test-rules.pro")
            matchingFallbacks += listOf("release")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("internal") {
            dimension = "environment"
        }

        create("external") { dimension = "environment" }
    }
}

// Example: Only enable on "externalStaging"
androidComponents {
    beforeVariants { variantBuilder ->
        if (variantBuilder.name == "externalStaging") {
            variantBuilder.optInToKeeper()
        }
    }
}

val maxVersionSupportedByR8 = JavaVersion.VERSION_21
if (JavaVersion.current() > maxVersionSupportedByR8) {
    tasks.withType<InferAndroidTestKeepRules>().configureEach {
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(maxVersionSupportedByR8.majorVersion)
            },
        )
    }
}

keeper {
    // Example: emit extra debug information during Keeper's execution.
    emitDebugInformation.set(true)

    // Example: automatic R8 repo management (more below)
    automaticR8RepoManagement.set(false)

    // Uncomment this line to debug the R8 from a remote instance.
    // r8JvmArgs.addAll(Arrays.asList("-Xdebug",
    // "-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y"))

    traceReferences {
        // Don't fail the build if missing definitions are found.
        arguments.set(listOf("--map-diagnostics:MissingDefinitionsDiagnostic", "error", "info"))
    }
}

// TODO create a dependent lifecycle task
tasks.withType<InferAndroidTestKeepRules>().configureEach {
    val expectedFile = file("expectedRules.pro")
    doLast {
        println("Checking expected rules")
        val outputFile = outputProguardRules.asFile.get()
        val outputRules = outputFile.readText().trim()
        val expectedRules = expectedFile.readText().trim()
        if (outputRules != expectedRules) {
            System.err.println(
                """
            Rules don't match expected
            Actual: file://$outputFile
            Expected: file://$expectedFile
          """
                    .trimIndent(),
            )
            assertThat(outputRules).isEqualTo(expectedRules)
        }
    }
}

tasks
    .withType<R8Task>()
    .matching { it.name == "minifyExternalStagingWithR8" }
    .configureEach {
        val value = findOutputAccessorValue()
        doLast {

            println("Checking expected configuration contains embedded library rules from androidTest")
            val allConfigurations = value.get().readText()
            logger.lifecycle("Verifying R8 configuration contents")
            if ("-keep class slack.test.only.Android { *; }" !in allConfigurations) {
                throw IllegalStateException(
                    "R8 configuration doesn't contain embedded library rules from androidTest. Full contents:\n$allConfigurations",
                )
            }
            if ("-keep class slack.test.only.Embedded { *; }" !in allConfigurations) {
                throw IllegalStateException(
                    "R8 configuration doesn't contain embedded library rules from androidTest. Full contents:\n$allConfigurations",
                )
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
    coreLibraryDesugaring(libs.desugarJdkLibs)

    implementation(project(":sample-libraries:a"))
    implementation(project(":sample-libraries:c"))
    implementation(libs.okio)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.truth)
    implementation(libs.junit)
    implementation(libs.truth)
    implementation(project(":sample-libraries:test-only-android"))
    implementation(project(":sample-libraries:test-only-jvm"))
}
