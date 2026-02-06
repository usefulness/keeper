import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

plugins {
    alias(libs.plugins.agp.library) apply false
    alias(libs.plugins.starter.library.kotlin) apply false
    // Version just here to make gradle happy. It's always substituted as an included build
    id("com.slack.keeper") version "0.16.1" apply false
}


subprojects {
    pluginManager.withPlugin("java") {
        configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

        tasks.withType<JavaCompile>().configureEach { options.release.set(11) }
    }

    plugins.withType<KotlinBasePlugin>().configureEach {
        project.tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.add("-progressive")
            }
        }
    }
}
