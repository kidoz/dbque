package su.kidoz.feature.editor.validation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import su.kidoz.core.model.DatabaseType
import su.kidoz.feature.parser.QueryParserService
import su.kidoz.feature.parser.sql.SqlDialect
import su.kidoz.feature.parser.validation.DatabaseVersion
import su.kidoz.feature.parser.validation.ValidationIssue
import su.kidoz.feature.parser.validation.ValidationResult

/**
 * Result of live validation
 */
data class LiveValidationResult(
    val tabId: String,
    val issues: List<ValidationIssue>,
    val hasErrors: Boolean,
    val hasWarnings: Boolean,
)

/**
 * Input for validation
 */
data class ValidationInput(
    val tabId: String,
    val content: String,
    val databaseType: DatabaseType? = null,
    val version: DatabaseVersion? = null,
    val tables: Set<String> = emptySet(),
)

/**
 * Provides debounced live validation for SQL/MongoDB/Elasticsearch queries.
 * Validation runs on Dispatchers.Default for performance.
 */
class LiveValidator(
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private val logger = KotlinLogging.logger {}

    private val parserService = QueryParserService()
    private val validationInput = MutableSharedFlow<ValidationInput>(replay = 1)

    /**
     * Flow of validation results, debounced and processed on Default dispatcher
     */
    @OptIn(FlowPreview::class)
    val validationResults: Flow<LiveValidationResult> =
        validationInput
            .debounce(debounceMs)
            .map { input -> validate(input) }
            .flowOn(Dispatchers.Default)

    /**
     * Submit content for validation.
     * Results will be emitted to validationResults after debounce period.
     */
    suspend fun submitForValidation(
        tabId: String,
        content: String,
        databaseType: DatabaseType? = null,
        version: DatabaseVersion? = null,
        tables: Set<String> = emptySet(),
    ) {
        validationInput.emit(ValidationInput(tabId, content, databaseType, version, tables))
    }

    /**
     * Validate content immediately (synchronous, blocking)
     */
    fun validateImmediate(content: String): ValidationResult =
        if (content.isBlank()) {
            ValidationResult(emptyList(), null)
        } else {
            parserService.validate(content)
        }

    private fun validate(input: ValidationInput): LiveValidationResult {
        if (input.content.isBlank()) {
            return LiveValidationResult(
                tabId = input.tabId,
                issues = emptyList(),
                hasErrors = false,
                hasWarnings = false,
            )
        }

        return try {
            val parserType =
                when (input.databaseType) {
                    DatabaseType.MONGODB -> su.kidoz.feature.parser.highlight.DatabaseType.MONGODB

                    DatabaseType.ELASTICSEARCH -> su.kidoz.feature.parser.highlight.DatabaseType.ELASTICSEARCH

                    DatabaseType.POSTGRESQL,
                    DatabaseType.MYSQL,
                    DatabaseType.SQLITE,
                    DatabaseType.H2,
                    -> su.kidoz.feature.parser.highlight.DatabaseType.SQL

                    null -> null
                }

            val dialect =
                when (input.databaseType) {
                    DatabaseType.MYSQL -> SqlDialect.MYSQL
                    DatabaseType.SQLITE -> SqlDialect.SQLITE
                    else -> SqlDialect.POSTGRESQL
                }

            val result =
                if (parserType != null) {
                    parserService.validateAs(
                        query = input.content,
                        type = parserType,
                        dialect = dialect,
                        version = input.version,
                        availableTables = input.tables,
                    )
                } else {
                    parserService.validate(input.content, input.version, input.tables)
                }

            LiveValidationResult(
                tabId = input.tabId,
                issues = result.issues,
                hasErrors = result.hasErrors,
                hasWarnings = result.hasWarnings,
            )
        } catch (e: Exception) {
            logger.error(e) { "Validation failed for tab ${input.tabId}" }
            LiveValidationResult(
                tabId = input.tabId,
                issues = emptyList(),
                hasErrors = false,
                hasWarnings = false,
            )
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 300L
    }
}
