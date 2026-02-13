/*
 * Copyright (C) 2020. Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.starter.library.kotlin)
    alias(libs.plugins.binaryCompatibilityValidator)
    id("org.jetbrains.kotlin.plugin.sam.with.receiver") version libs.versions.maven.kotlin.get()
    alias(libs.plugins.gradle.pluginpublish)
    id("com.starter.publishing")
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.java.compilation.get().toInt())
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
        freeCompilerArgs.add("-Xsam-conversions=class")
    }
}

tasks.withType<Test>().configureEach {
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("Running test: $this") })
    // Required to test configuration cache in tests when using withDebug()
    // https://github.com/gradle/gradle/issues/22765#issuecomment-1339427241
    jvmArgs(
        "--add-opens",
        "java.base/java.util=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.net=ALL-UNNAMED",
    )
}

gradlePlugin {
    plugins {
        plugins.create("keeper") {
            id = "io.github.usefulness.keeper"
            implementationClass = "com.slack.keeper.KeeperPlugin"
            displayName = "A Gradle plugin that infers Proguard/R8 keep rules for androidTest sources."
        }
    }
}

dokka {
    dokkaPublications.configureEach {
        suppressInheritedMembers.set(true)
        outputDirectory.set(rootDir.resolve("../docs/0.x"))
    }
    dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
    }
}

ktlint {
    ktlintVersion = libs.versions.maven.ktlint.get()
}

val testRuntimeDependencies = configurations.register("testRuntimeDependencies") {
    attributes {
        // KGP publishes multiple variants https://kotlinlang.org/docs/whatsnew17.html#support-for-gradle-plugin-variants
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class, Category.LIBRARY))
    }
}

tasks.withType<PluginUnderTestMetadata>().configureEach {
    pluginClasspath.from(testRuntimeDependencies)
}

if (project.hasProperty("skipJarVersion")) {
    tasks.named<Jar>("jar") {
        archiveVersion.set("")
    }
}

tasks.register<WriteProperties>("generateVersionProperties") {
    val propertiesFile = File(sourceSets.main.get().output.resourcesDir, "keeper-gradle-plugin.properties")
    destinationFile = propertiesFile
    property("keeper.r8_version", libs.versions.google.r8.get())
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

dependencies {
    compileOnly(libs.kgp.api)
    compileOnly(libs.zipflinger)
    compileOnly(libs.agp.impl)
    testRuntimeDependencies(libs.agp.impl)
    testRuntimeDependencies(libs.kgp.impl)
    testImplementation(libs.javapoet)
    testImplementation(libs.kotlinpoet)
    testImplementation(libs.truth)
    testImplementation(libs.junit)
}
