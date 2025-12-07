plugins {
    kotlin("jvm")
    `java-library`
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
    api(project(":aggo-core"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    implementation(gradleApi())

    // Database drivers for CLI tools (generateMirrors task)
    runtimeOnly("org.postgresql:postgresql:42.7.2")
    runtimeOnly("com.mysql:mysql-connector-j:8.3.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation("org.testcontainers:mysql:1.19.7")
    testImplementation("com.mysql:mysql-connector-j:8.3.0")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("AggORM Migrations")
                description.set("Database migrations module for AggORM with type-safe schema management")
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

// ==================== Mirror Generation Task ====================

/**
 * Gradle task to generate Mirror files from database schema.
 *
 * Usage:
 * ```bash
 * ./gradlew :aggo-migrations:generateMirrors \
 *     -PjdbcUrl=jdbc:postgresql://localhost:5432/mydb \
 *     -PjdbcUser=postgres \
 *     -PjdbcPassword=secret \
 *     -PbasePackage=com.myapp.generated \
 *     -PoutputDir=src/main/kotlin/com/myapp/generated \
 *     -PschemaName=public
 * ```
 *
 * Or configure in gradle.properties:
 * ```properties
 * jdbcUrl=jdbc:postgresql://localhost:5432/mydb
 * jdbcUser=postgres
 * jdbcPassword=secret
 * basePackage=com.myapp.generated
 * outputDir=src/main/kotlin/com/myapp/generated
 * ```
 */
tasks.register<JavaExec>("generateMirrors") {
    group = "aggo"
    description = "Generates Mirror files from database schema for type-safe queries"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.aggitech.orm.migrations.generator.MirrorGeneratorCli")

    // Pass project properties as system properties
    systemProperties(
        mapOf(
            "jdbcUrl" to findProperty("jdbcUrl"),
            "jdbcUser" to findProperty("jdbcUser"),
            "jdbcPassword" to findProperty("jdbcPassword"),
            "basePackage" to (findProperty("basePackage") ?: "generated"),
            "outputDir" to (findProperty("outputDir") ?: "src/main/kotlin/generated"),
            "schemaName" to (findProperty("schemaName") ?: "public")
        ).filterValues { it != null }
    )

    // Ensure PostgreSQL driver is available
    doFirst {
        if (!project.hasProperty("jdbcUrl")) {
            throw GradleException("""
                |Missing required property: jdbcUrl
                |
                |Usage:
                |  ./gradlew :aggo-migrations:generateMirrors \
                |      -PjdbcUrl=jdbc:postgresql://localhost:5432/mydb \
                |      -PjdbcUser=postgres \
                |      -PjdbcPassword=secret \
                |      -PbasePackage=com.myapp.generated \
                |      -PoutputDir=src/main/kotlin/com/myapp/generated
            """.trimMargin())
        }
    }
}
