package su.kidoz.feature.editor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuerySplitterTest {
    @Test
    fun split_supportsDollarTagsStartingWithUnderscore() {
        val sql =
            "SELECT 1;" +
                "DO \$_$ BEGIN SELECT 2; END \$_$;" +
                "SELECT 3;"

        val result = QuerySplitter.split(sql)

        assertEquals(3, result.nonEmptyQueries.size)
        assertTrue(result.nonEmptyQueries[1].query.contains("SELECT 2;"))
    }
}
