plugins {
    kotlin("jvm")
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
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.9.0")
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.0.RELEASE")

    // YAML support (optional - for application.yml/yaml loading)
    compileOnly("org.yaml:snakeyaml:2.2")

    testImplementation(kotlin("test"))
    testImplementation("org.yaml:snakeyaml:2.2")
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
