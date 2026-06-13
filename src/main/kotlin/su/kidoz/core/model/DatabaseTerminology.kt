package su.kidoz.core.model

/**
 * Category of database for UI adaptation.
 */
enum class DatabaseCategory {
    RELATIONAL,
    DOCUMENT,
    SEARCH_ENGINE,
}

/**
 * UI terminology that adapts based on database type.
 * This allows the UI to show appropriate labels like "Tables" for SQL,
 * "Collections" for MongoDB, and "Indices" for Elasticsearch.
 */
data class DatabaseTerminology(
    val category: DatabaseCategory,
    /** Label for the tables/collections/indices folder */
    val tableLabel: String,
    /** Label for a single table/collection/index */
    val tableSingular: String,
    /** Label for rows/documents */
    val rowLabel: String,
    /** Label for columns/fields */
    val columnLabel: String,
    /** Label for schema (null if not applicable) */
    val schemaLabel: String?,
    /** Context menu action for select/find/search */
    val selectAction: String,
    /** Context menu action for insert */
    val insertAction: String,
    /** Context menu action for DDL/structure */
    val ddlAction: String,
    /** Whether this database type supports views */
    val supportsViews: Boolean,
    /** Whether this database type supports schemas */
    val supportsSchemas: Boolean,
)

/**
 * Get the appropriate UI terminology for this database type.
 */
fun DatabaseType.terminology(): DatabaseTerminology =
    when (this) {
        DatabaseType.POSTGRESQL,
        DatabaseType.MYSQL,
        DatabaseType.SQLITE,
        DatabaseType.H2,
        -> {
            DatabaseTerminology(
                category = DatabaseCategory.RELATIONAL,
                tableLabel = "Tables",
                tableSingular = "Table",
                rowLabel = "Rows",
                columnLabel = "Columns",
                schemaLabel = "Schema",
                selectAction = "SELECT * FROM...",
                insertAction = "INSERT INTO...",
                ddlAction = "Generate DDL",
                supportsViews = true,
                supportsSchemas = true,
            )
        }

        DatabaseType.MONGODB -> {
            DatabaseTerminology(
                category = DatabaseCategory.DOCUMENT,
                tableLabel = "Collections",
                tableSingular = "Collection",
                rowLabel = "Documents",
                columnLabel = "Fields",
                schemaLabel = null,
                selectAction = "Find Documents...",
                insertAction = "Insert Document...",
                ddlAction = "Collection Info",
                supportsViews = false,
                supportsSchemas = false,
            )
        }

        DatabaseType.ELASTICSEARCH -> {
            DatabaseTerminology(
                category = DatabaseCategory.SEARCH_ENGINE,
                tableLabel = "Indices",
                tableSingular = "Index",
                rowLabel = "Documents",
                columnLabel = "Fields",
                schemaLabel = "Mappings",
                selectAction = "Search...",
                insertAction = "Index Document...",
                ddlAction = "Index Mapping",
                supportsViews = false,
                supportsSchemas = false,
            )
        }
    }
