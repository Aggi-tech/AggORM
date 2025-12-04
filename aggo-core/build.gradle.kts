plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "io.github.aggi-tech"
version = "1.0.5"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.9.0")
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.0.RELEASE")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("AggORM Core")
                description.set("Type-safe ORM framework for Kotlin with declarative DSL")
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
