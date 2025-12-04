plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "aggo-orm"

include("aggo-core")
include("aggo-spring-boot-starter")