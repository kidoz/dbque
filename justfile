# DBQue - Database Management Tool
# Run commands with: just <command>

# Default command - show available commands
default:
    @just --list

# Run the desktop application
run:
    ./gradlew run

# Build the project (excludes detekt due to Gradle 9 incompatibility)
build:
    ./gradlew build -x detekt

# Clean and rebuild
rebuild:
    ./gradlew clean build -x detekt

# Run tests
test:
    ./gradlew test

# Check code style with ktlint
lint:
    ./gradlew ktlintCheck

# Auto-fix code style issues
lint-fix:
    ./gradlew ktlintFormat

# Generate SQLDelight code
generate-db:
    ./gradlew generateMainDatabaseInterface

# Check for dependency updates
deps:
    ./gradlew dependencyUpdates

# Create distribution packages
package:
    ./gradlew packageDistributionForCurrentOS -x detekt
