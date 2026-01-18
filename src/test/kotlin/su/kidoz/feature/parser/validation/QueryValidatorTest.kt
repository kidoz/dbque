package su.kidoz.feature.parser.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryValidatorTest {
    private val validator = SqlValidator()

    @Test
    fun validate_validSelect_succeeds() {
        val result = validator.validate("SELECT id, name FROM users WHERE id = 1")

        assertTrue(result.isValid)
        assertFalse(result.hasErrors)
    }

    @Test
    fun validate_selectStar_showsWarning() {
        val result = validator.validate("SELECT * FROM users")

        assertTrue(result.isValid)
        assertTrue(result.hasWarnings)
        assertTrue(result.issues.any { it.code == "SQL101" })
    }

    @Test
    fun validate_selectWithoutWhere_showsInfo() {
        val result = validator.validate("SELECT id FROM users")

        assertTrue(result.isValid)
        assertTrue(result.issues.any { it.code == "SQL102" })
    }

    @Test
    fun validate_nullComparisonWithEquals_showsWarning() {
        val result = validator.validate("SELECT * FROM users WHERE email = NULL")

        assertTrue(result.hasWarnings)
        assertTrue(result.issues.any { it.code == "SQL301" })
    }

    @Test
    fun validate_coalesceWithOneArg_showsWarning() {
        val result = validator.validate("SELECT COALESCE(name) FROM users")

        assertTrue(result.issues.any { it.code == "SQL402" })
    }

    @Test
    fun validate_updateWithoutWhere_showsWarning() {
        val result = validator.validate("UPDATE users SET active = FALSE")

        assertTrue(result.hasWarnings)
        assertTrue(result.issues.any { it.code == "SQL801" })
    }

    @Test
    fun validate_deleteWithoutWhere_showsWarning() {
        val result = validator.validate("DELETE FROM users")

        assertTrue(result.hasWarnings)
        assertTrue(result.issues.any { it.code == "SQL901" })
    }

    @Test
    fun validate_insertColumnMismatch_showsError() {
        val result = validator.validate("INSERT INTO users (name, email) VALUES ('John')")

        assertTrue(result.hasErrors)
        assertTrue(result.issues.any { it.code == "SQL701" })
    }

    @Test
    fun validate_syntaxError_returnsError() {
        val result = validator.validate("SELEC FROM")

        assertFalse(result.isValid)
        assertTrue(result.hasErrors)
        assertTrue(result.issues.any { it.code == "SQL001" })
    }

    @Test
    fun validate_simpleQuery_succeeds() {
        val result = validator.validate("SELECT name FROM users WHERE active = TRUE")

        assertTrue(result.isValid)
    }
}

class DatabaseVersionTest {
    @Test
    fun parse_fullVersion_succeeds() {
        val version = DatabaseVersion.parse("15.2.1")

        assertEquals(15, version.major)
        assertEquals(2, version.minor)
        assertEquals(1, version.patch)
    }

    @Test
    fun parse_majorMinorOnly_succeeds() {
        val version = DatabaseVersion.parse("14.5")

        assertEquals(14, version.major)
        assertEquals(5, version.minor)
        assertEquals(0, version.patch)
    }

    @Test
    fun parse_majorOnly_succeeds() {
        val version = DatabaseVersion.parse("8")

        assertEquals(8, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
    }

    @Test
    fun compare_versions_correct() {
        val versionFifteen = DatabaseVersion(15, 0)
        val versionFourteenFive = DatabaseVersion(14, 5)
        val versionFourteenFiveOne = DatabaseVersion(14, 5, 1)

        assertTrue(versionFifteen > versionFourteenFive)
        assertTrue(versionFourteenFiveOne > versionFourteenFive)
        assertTrue(versionFourteenFive < versionFifteen)
    }
}
