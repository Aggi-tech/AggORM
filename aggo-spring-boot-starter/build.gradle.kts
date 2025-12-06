plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
    `maven-publish`
    signing
}

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

    // Dependência do módulo migrations (opcional)
    api(project(":aggo-migrations"))

    // Spring Boot core (sem JPA - framework-agnostic)
    // Using api to allow consumers to use these classes
    api("org.springframework.boot:spring-boot-autoconfigure:3.2.0")
    api("org.springframework:spring-context:6.1.0")
    api("org.springframework:spring-tx:6.1.0")

    // Spring Data Commons para compatibilidade com CrudRepository (opcional)
    compileOnly("org.springframework.data:spring-data-commons:3.2.0")

    // JPA é opcional - só para integração se o usuário já usar
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // Configuration processor para gerar metadata de propriedades
    kapt("org.springframework.boot:spring-boot-configuration-processor:3.2.0")

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

    repositories {
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = providers.gradleProperty("ossrhUsername").orNull
                password = providers.gradleProperty("ossrhPassword").orNull
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
