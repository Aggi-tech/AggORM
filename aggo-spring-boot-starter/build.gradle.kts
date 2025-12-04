plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.2.20"
}

group = "com.aggitech.orm"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Dependência do módulo core
    api(project(":aggo-core"))

    // Spring Boot e JPA (dependencies obrigatórias para o starter)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.2.0")
    implementation("org.springframework:spring-context:6.1.0")
    implementation("org.springframework:spring-tx:6.1.0")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.2.0")

    // Reflection e Kotlin
    implementation(kotlin("reflect"))

    // Testes
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.0")
}

tasks.test {
    useJUnitPlatform()
}
