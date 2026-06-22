package su.kidoz.database.capabilities

/**
 * Represents a database version with comparison support.
 */
data class DatabaseVersion(
    val major: Int,
    val minor: Int = 0,
    val patch: Int = 0,
    val fullVersion: String = "$major.$minor.$patch",
    val productName: String = "",
) : Comparable<DatabaseVersion> {
    override fun compareTo(other: DatabaseVersion): Int =
        when {
            major != other.major -> major.compareTo(other.major)
            minor != other.minor -> minor.compareTo(other.minor)
            else -> patch.compareTo(other.patch)
        }

    fun isAtLeast(
        major: Int,
        minor: Int = 0,
        patch: Int = 0,
    ): Boolean = this >= DatabaseVersion(major, minor, patch)

    override fun toString(): String = fullVersion

    companion object {
        val UNKNOWN = DatabaseVersion(0, 0, 0, "Unknown", "Unknown")

        /**
         * Parse version string like "14.5.0", "8.0.33", "3.39.0"
         */
        fun parse(
            versionString: String,
            productName: String = "",
        ): DatabaseVersion =
            try {
                // Extract version numbers from string (handles formats like "14.5", "8.0.33-ubuntu", etc.)
                val cleaned = versionString.replace(Regex("[^0-9.]"), " ").trim()
                val numbers =
                    cleaned
                        .split(Regex("[.\\s]+"))
                        .filter { it.isNotEmpty() }
                        .take(3)
                        .map { it.toIntOrNull() ?: 0 }

                DatabaseVersion(
                    major = numbers.getOrElse(0) { 0 },
                    minor = numbers.getOrElse(1) { 0 },
                    patch = numbers.getOrElse(2) { 0 },
                    fullVersion = versionString,
                    productName = productName,
                )
            } catch (e: Exception) {
                DatabaseVersion(0, 0, 0, versionString, productName)
            }

        /**
         * Parse PostgreSQL version from SELECT version() output
         * e.g., "PostgreSQL 14.5 on x86_64-pc-linux-gnu..."
         */
        fun parsePostgres(versionOutput: String): DatabaseVersion {
            val match = Regex("PostgreSQL\\s+([\\d.]+)").find(versionOutput)
            val version = match?.groupValues?.get(1) ?: "0.0.0"
            return parse(version, "PostgreSQL")
        }

        /**
         * Parse MySQL version from SELECT VERSION() output
         * e.g., "8.0.33-0ubuntu0.22.04.1"
         */
        fun parseMySql(versionOutput: String): DatabaseVersion {
            val version = versionOutput.substringBefore("-")
            return parse(version, "MySQL")
        }

        /**
         * Parse StarRocks version from SELECT current_version()
         * e.g., "4.0.0", "3.3.5-12345", "4.1.0 RELEASE"
         */
        fun parseStarRocks(versionOutput: String): DatabaseVersion = parse(versionOutput.substringBefore("-"), "StarRocks")

        /**
         * Parse SQLite version from SELECT sqlite_version()
         * e.g., "3.39.0"
         */
        fun parseSqlite(versionOutput: String): DatabaseVersion = parse(versionOutput, "SQLite")

        /**
         * Parse H2 version
         */
        fun parseH2(versionOutput: String): DatabaseVersion = parse(versionOutput, "H2")

        /**
         * Parse MongoDB version from buildInfo command output
         * e.g., "7.0.5", "6.0.12"
         */
        fun parseMongoDB(versionOutput: String): DatabaseVersion = parse(versionOutput, "MongoDB")

        /**
         * Parse Elasticsearch version from cluster info
         * e.g., "8.15.0", "7.17.12"
         */
        fun parseElasticsearch(versionOutput: String): DatabaseVersion = parse(versionOutput, "Elasticsearch")
    }
}
