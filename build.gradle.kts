plugins {
    alias(libs.plugins.starter.config)
    alias(libs.plugins.starter.versioning)
    alias(libs.plugins.agp.library) apply false
    alias(libs.plugins.starter.library.kotlin) apply false
}

commonConfig {
    javaVersion = JavaVersion.VERSION_17
}
