package su.kidoz.feature.editor.validation

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import su.kidoz.feature.parser.QueryParserService
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
            .distinctUntilChanged { old, new -> old.content == new.content && old.tabId == new.tabId }
            .map { input -> validate(input) }
            .flowOn(Dispatchers.Default)

    /**
     * Submit content for validation.
     * Results will be emitted to validationResults after debounce period.
     */
    suspend fun submitForValidation(
        tabId: String,
        content: String,
    ) {
        validationInput.emit(ValidationInput(tabId, content))
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
            val result = parserService.validate(input.content)
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
