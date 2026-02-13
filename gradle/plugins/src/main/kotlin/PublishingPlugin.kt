import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

class PublishingPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.vanniktech.maven.publish")
        pluginManager.apply("com.gradle.plugin-publish")
        pluginManager.apply("org.jetbrains.dokka")

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            tasks.named("processResources", ProcessResources::class.java) { processResources ->
                processResources.from(rootProject.file("LICENSE"))
            }

            tasks.named { it == "javadocJar" }.withType(Jar::class.java).configureEach { javadocJar ->
                javadocJar.from(tasks.named("dokkaGeneratePublicationHtml"))
            }
        }

        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            coordinates(group.toString(), name, version.toString())

            signAllPublications()

            configureBasedOnAppliedPlugins()

            pom { pom ->
                pom.name.set("${project.group}:${project.name}")
                pom.description.set(project.description)
                pom.url.set("https://github.com/usefulness/ktlint-gradle-plugin")
                pom.licenses { licenses ->
                    licenses.license { license ->
                        license.name.set("Apache-2.0")
                        license.url.set("https://github.com/usefulness/ktlint-gradle-plugin/blob/master/LICENSE")
                    }
                }
                pom.developers { developers ->
                    developers.developer { developer ->
                        developer.id.set("mateuszkwiecinski")
                        developer.name.set("Mateusz Kwiecinski")
                        developer.email.set("36954793+mateuszkwiecinski@users.noreply.github.com")
                    }
                    developers.developer { developer ->
                        developer.id.set("jeremymailen")
                        developer.name.set("Jeremy Mailen")
                    }
                }
                pom.scm { scm ->
                    scm.connection.set("scm:git:github.com/usefulness/keeper.git")
                    scm.developerConnection.set("scm:git:ssh://github.com/usefulness/keeper.git")
                    scm.url.set("https://github.com/usefulness/keeper/tree/master")
                }
            }
        }

        extensions.configure<PublishingExtension> {
            with(repositories) {
                maven { maven ->
                    maven.name = "github"
                    maven.setUrl("https://github.com/usefulness/keeper/")
                    with(maven.credentials) {
                        username = "usefulness"
                        password = findConfig("GITHUB_TOKEN")
                    }
                }
            }
        }
        pluginManager.withPlugin("com.gradle.plugin-publish") {
            extensions.configure<GradlePluginDevelopmentExtension> {
                website.set("https://github.com/usefulness/keeper/")
                vcsUrl.set("https://github.com/usefulness/keeper.git")
                plugins.configureEach { plugin ->
                    plugin.tags.set(listOf("android", "kotlin", "keeper", "proguard", "release", "android-test"))
                    plugin.description = "A Gradle plugin that infers Proguard/R8 keep rules for androidTest sources."
                    plugin.displayName = "A Gradle plugin that infers Proguard/R8 keep rules for androidTest sources."
                }
            }
        }
    }

    private inline fun <reified T : Any> ExtensionContainer.configure(crossinline receiver: T.() -> Unit) {
        configure(T::class.java) { receiver(it) }
    }
}

private fun Project.findConfig(key: String): String {
    return findProperty(key)?.toString() ?: System.getenv(key) ?: ""
}
