import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("app.cash.sqldelight") version "2.0.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
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

    // Koin - Dependency Injection
    implementation("io.insert-koin:koin-core:3.5.6")
    implementation("io.insert-koin:koin-compose:1.1.5")

    // Ktor - HTTP Client
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // SQLDelight - Local Storage
    implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")

    // kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // JDBC Drivers
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.h2database:h2:2.2.224")

    // MongoDB Driver
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.1.0")
    implementation("org.mongodb:bson-kotlin:5.1.0")

    // Elasticsearch Client
    implementation("co.elastic.clients:elasticsearch-java:8.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Connection Pool
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Parser Combinator Library
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.4")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
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
    version.set("1.5.0")
    filter {
        include("**/src/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/detekt.yml")
}
