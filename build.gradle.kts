import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "su.kidoz"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
}

dependencies {
    // Compose Multiplatform
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Koin - Dependency Injection (using BOM)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    // Ktor - HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // SQLDelight - Local Storage
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines)

    // kotlinx.serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    // JDBC Drivers
    implementation(libs.postgresql)
    implementation(libs.mysql.connector)
    implementation(libs.sqlite.jdbc)
    implementation(libs.h2)

    // MongoDB Driver
    implementation(libs.mongodb.driver.kotlin)
    implementation(libs.bson.kotlin)

    // Elasticsearch Client
    implementation(libs.elasticsearch.java)
    implementation(libs.jackson.databind)

    // Connection Pool
    implementation(libs.hikaricp)

    // Parser Combinator Library
    implementation(libs.better.parse)

    // SSH Tunnel Support
    implementation(libs.jsch)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("su.kidoz.storage")
            srcDirs("src/main/sqldelight")
        }
    }
}

compose.desktop {
    application {
        mainClass = "su.kidoz.AppKt"

        jvmArgs +=
            listOf(
                "-Xdock:name=DBQue",
            )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DBQue"
            packageVersion = "1.0.0"
            description = "Database Management Tool"
            vendor = "DBQue"

            macOS {
                iconFile.set(project.file("src/main/resources/icons/app.icns"))
                bundleID = "su.kidoz.app"
            }

            windows {
                iconFile.set(project.file("src/main/resources/icons/app.ico"))
                menuGroup = "DBQue"
            }

            linux {
                iconFile.set(project.file("src/main/resources/icons/app.png"))
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set(libs.versions.ktlint.lib)
    filter {
        include("**/src/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/config/detekt/detekt.yml")
}
