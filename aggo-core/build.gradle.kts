plugins {
    kotlin("jvm")
}

group = "com.aggitech.orm"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
