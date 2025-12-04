plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.2.20"
    `maven-publish`
}

group = "io.github.aggi-tech"
version = "1.0.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("AggORM Spring Boot Starter")
                description.set("Spring Boot starter for AggORM - Type-safe ORM framework for Kotlin")
                url.set("https://github.com/aggi-tech/aggorm")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("yurimoinhos")
                        name.set("Yuri Moinhos")
                        email.set("moinhosyuri@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/aggi-tech/aggorm.git")
                    developerConnection.set("scm:git:ssh://github.com/aggi-tech/aggorm.git")
                    url.set("https://github.com/aggi-tech/aggorm")
                }
            }
        }
    }
}
