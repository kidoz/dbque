package su.kidoz.core.model

data class DatabaseInfo(
    val name: String,
    val schemas: List<SchemaInfo> = emptyList(),
)

data class SchemaInfo(
    val name: String,
    val catalog: String? = null,
    val tables: List<TableInfo> = emptyList(),
    val views: List<ViewInfo> = emptyList(),
)

data class TableInfo(
    val name: String,
    val schema: String? = null,
    val catalog: String? = null,
    val type: TableType = TableType.TABLE,
    val columns: List<ColumnInfo> = emptyList(),
    val primaryKey: PrimaryKeyInfo? = null,
    val foreignKeys: List<ForeignKeyInfo> = emptyList(),
    val indexes: List<IndexInfo> = emptyList(),
    val estimatedRowCount: Long? = null,
    val comment: String? = null,
)

enum class TableType {
    TABLE,
    VIEW,
    SYSTEM_TABLE,
    MATERIALIZED_VIEW,
}

data class ViewInfo(
    val name: String,
    val schema: String? = null,
    val catalog: String? = null,
    val columns: List<ColumnInfo> = emptyList(),
    val definition: String? = null,
    val comment: String? = null,
)

data class ColumnInfo(
    val name: String,
    val dataType: String,
    val jdbcType: Int,
    val nullable: Boolean,
    val size: Int? = null,
    val precision: Int? = null,
    val scale: Int? = null,
    val defaultValue: String? = null,
    val autoIncrement: Boolean = false,
    val ordinalPosition: Int = 0,
    val comment: String? = null,
) {
    val typeDisplay: String
        get() {
            val base = dataType
            return when {
                size != null && precision != null && scale != null && scale > 0 -> {
                    "$base($precision,$scale)"
                }

                size != null && size > 0 -> {
                    "$base($size)"
                }

                precision != null && precision > 0 -> {
                    "$base($precision)"
                }

                else -> {
                    base
                }
            }
        }
}

data class PrimaryKeyInfo(
    val name: String?,
    val columns: List<String>,
)

data class ForeignKeyInfo(
    val name: String?,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val updateRule: ForeignKeyRule = ForeignKeyRule.NO_ACTION,
    val deleteRule: ForeignKeyRule = ForeignKeyRule.NO_ACTION,
)

enum class ForeignKeyRule {
    CASCADE,
    SET_NULL,
    SET_DEFAULT,
    RESTRICT,
    NO_ACTION,
}

data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false,
    val type: IndexType = IndexType.BTREE,
)

enum class IndexType {
    BTREE,
    HASH,
    FULLTEXT,
    SPATIAL,
    OTHER,
}
