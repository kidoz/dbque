package su.kidoz.feature.queryplan

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryPlanParserTest {
    private val parser = QueryPlanParser()

    @Test
    fun parsePostgresPlan_simpleSeqScan() {
        val jsonPlan =
            """
            [
              {
                "Plan": {
                  "Node Type": "Seq Scan",
                  "Relation Name": "users",
                  "Alias": "users",
                  "Startup Cost": 0.0,
                  "Total Cost": 10.5,
                  "Plan Rows": 100,
                  "Plan Width": 40
                }
              }
            ]
            """.trimIndent()

        val nodes = parser.parsePostgresPlan(jsonPlan)

        assertEquals(1, nodes.size)
        assertEquals("Seq Scan", nodes[0].nodeType)
        assertEquals("users", nodes[0].relationName)
        assertEquals(0.0, nodes[0].startupCost)
        assertEquals(10.5, nodes[0].totalCost)
        assertEquals(100L, nodes[0].planRows)
        assertEquals(40, nodes[0].planWidth)
    }

    @Test
    fun parsePostgresPlan_withChildren() {
        val jsonPlan =
            """
            [
              {
                "Plan": {
                  "Node Type": "Hash Join",
                  "Join Type": "Inner",
                  "Hash Cond": "(a.id = b.id)",
                  "Startup Cost": 5.0,
                  "Total Cost": 100.0,
                  "Plan Rows": 50,
                  "Plan Width": 80,
                  "Plans": [
                    {
                      "Node Type": "Seq Scan",
                      "Relation Name": "table_a",
                      "Alias": "a",
                      "Startup Cost": 0.0,
                      "Total Cost": 10.0,
                      "Plan Rows": 100,
                      "Plan Width": 40
                    },
                    {
                      "Node Type": "Hash",
                      "Startup Cost": 5.0,
                      "Total Cost": 5.0,
                      "Plan Rows": 100,
                      "Plan Width": 40,
                      "Plans": [
                        {
                          "Node Type": "Seq Scan",
                          "Relation Name": "table_b",
                          "Alias": "b",
                          "Startup Cost": 0.0,
                          "Total Cost": 5.0,
                          "Plan Rows": 100,
                          "Plan Width": 40
                        }
                      ]
                    }
                  ]
                }
              }
            ]
            """.trimIndent()

        val nodes = parser.parsePostgresPlan(jsonPlan)

        assertEquals(1, nodes.size)
        assertEquals("Hash Join", nodes[0].nodeType)
        assertEquals("Inner", nodes[0].joinType)
        assertEquals(2, nodes[0].children.size)
        assertEquals("Seq Scan", nodes[0].children[0].nodeType)
        assertEquals("Hash", nodes[0].children[1].nodeType)
        assertEquals(1, nodes[0].children[1].children.size)
    }

    @Test
    fun parsePostgresPlan_withAnalyze() {
        val jsonPlan =
            """
            [
              {
                "Plan": {
                  "Node Type": "Seq Scan",
                  "Relation Name": "users",
                  "Startup Cost": 0.0,
                  "Total Cost": 10.5,
                  "Plan Rows": 100,
                  "Plan Width": 40,
                  "Actual Startup Time": 0.01,
                  "Actual Total Time": 1.5,
                  "Actual Rows": 95,
                  "Actual Loops": 1
                }
              }
            ]
            """.trimIndent()

        val nodes = parser.parsePostgresPlan(jsonPlan)

        assertEquals(1, nodes.size)
        assertEquals(0.01, nodes[0].actualStartupTime)
        assertEquals(1.5, nodes[0].actualTotalTime)
        assertEquals(95L, nodes[0].actualRows)
        assertEquals(1L, nodes[0].actualLoops)
    }

    @Test
    fun parsePostgresPlan_withIndex() {
        val jsonPlan =
            """
            [
              {
                "Plan": {
                  "Node Type": "Index Scan",
                  "Relation Name": "users",
                  "Index Name": "users_pkey",
                  "Index Cond": "(id = 1)",
                  "Startup Cost": 0.0,
                  "Total Cost": 1.0,
                  "Plan Rows": 1,
                  "Plan Width": 40
                }
              }
            ]
            """.trimIndent()

        val nodes = parser.parsePostgresPlan(jsonPlan)

        assertEquals(1, nodes.size)
        assertEquals("Index Scan", nodes[0].nodeType)
        assertEquals("users_pkey", nodes[0].indexName)
        assertEquals("(id = 1)", nodes[0].indexCond)
    }

    @Test
    fun parsePostgresPlan_invalidJson_returnsEmpty() {
        val invalidJson = "not valid json"

        val nodes = parser.parsePostgresPlan(invalidJson)

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun parsePostgresPlan_emptyPlan_returnsEmpty() {
        val emptyPlan = "[]"

        val nodes = parser.parsePostgresPlan(emptyPlan)

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun parseMySqlPlan_simpleSelect() {
        val explainOutput =
            listOf(
                listOf(1, "SIMPLE", "users", "ALL", null, null, null, null, 100, null),
            )

        val nodes = parser.parseMySqlPlan(explainOutput)

        assertEquals(1, nodes.size)
        assertEquals("ALL", nodes[0].nodeType)
        assertEquals("SIMPLE", nodes[0].operation)
        assertEquals("users", nodes[0].relationName)
        assertEquals(100L, nodes[0].planRows)
    }

    @Test
    fun parseMySqlPlan_withIndex() {
        val explainOutput =
            listOf(
                listOf(1, "SIMPLE", "users", "ref", "idx_email", "idx_email", "255", "const", 1, "Using index"),
            )

        val nodes = parser.parseMySqlPlan(explainOutput)

        assertEquals(1, nodes.size)
        assertEquals("ref", nodes[0].nodeType)
        assertEquals("idx_email", nodes[0].indexName)
        assertEquals("Using index", nodes[0].filter)
    }

    @Test
    fun parseMySqlPlan_emptyExplain_returnsEmpty() {
        val explainOutput = emptyList<List<Any?>>()

        val nodes = parser.parseMySqlPlan(explainOutput)

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun parseGenericPlan_simplePlan() {
        val textPlan =
            """
            Seq Scan on users  (cost=0.00..10.50 rows=100 width=40)
            """.trimIndent()

        val nodes = parser.parseGenericPlan(textPlan)

        assertEquals(1, nodes.size)
        assertEquals("Seq Scan on users", nodes[0].nodeType)
    }

    @Test
    fun parseGenericPlan_nestedPlan() {
        val textPlan =
            """
            Hash Join  (cost=100.00..200.00 rows=50 width=80)
              -> Seq Scan on a  (cost=0.00..10.00 rows=100 width=40)
              -> Hash  (cost=10.00..10.00 rows=100 width=40)
                -> Seq Scan on b  (cost=0.00..5.00 rows=100 width=40)
            """.trimIndent()

        val nodes = parser.parseGenericPlan(textPlan)

        assertTrue(nodes.isNotEmpty())
        assertEquals("Hash Join", nodes[0].nodeType)
    }

    @Test
    fun parseGenericPlan_emptyPlan_returnsEmpty() {
        val textPlan = ""

        val nodes = parser.parseGenericPlan(textPlan)

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun parseGenericPlan_blankLines_ignored() {
        val textPlan =
            """

            Seq Scan on users  (cost=0.00..10.50 rows=100 width=40)

            """.trimIndent()

        val nodes = parser.parseGenericPlan(textPlan)

        assertEquals(1, nodes.size)
    }
}

class QueryPlanNodeTest {
    @Test
    fun displayName_simpleNode() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Seq Scan",
                operation = "Seq Scan",
            )

        assertEquals("Seq Scan", node.displayName)
    }

    @Test
    fun displayName_withRelation() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Seq Scan",
                operation = "Seq Scan",
                relationName = "users",
            )

        assertEquals("Seq Scan on users", node.displayName)
    }

    @Test
    fun displayName_withAlias() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Seq Scan",
                operation = "Seq Scan",
                relationName = "users",
                alias = "u",
            )

        assertEquals("Seq Scan on users (u)", node.displayName)
    }

    @Test
    fun displayName_aliasSameAsRelation_noParens() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Seq Scan",
                operation = "Seq Scan",
                relationName = "users",
                alias = "users",
            )

        assertEquals("Seq Scan on users", node.displayName)
    }

    @Test
    fun isExpensive_lowCost_false() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Index Scan",
                operation = "Index Scan",
                totalCost = 10.0,
            )

        assertFalse(node.isExpensive)
    }

    @Test
    fun isExpensive_highCost_true() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Seq Scan",
                operation = "Seq Scan",
                totalCost = 5000.0,
            )

        assertTrue(node.isExpensive)
    }

    @Test
    fun isExpensive_nullCost_false() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Result",
                operation = "Result",
                totalCost = null,
            )

        assertFalse(node.isExpensive)
    }

    @Test
    fun costPercentage_returnsTotalCost() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Seq Scan",
                operation = "Seq Scan",
                totalCost = 42.5,
            )

        assertEquals(42.5, node.costPercentage)
    }

    @Test
    fun costPercentage_nullCost_returnsZero() {
        val node =
            QueryPlanNode(
                id = "1",
                nodeType = "Result",
                operation = "Result",
                totalCost = null,
            )

        assertEquals(0.0, node.costPercentage)
    }
}
