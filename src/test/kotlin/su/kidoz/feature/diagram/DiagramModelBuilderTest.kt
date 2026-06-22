package su.kidoz.feature.diagram

import su.kidoz.core.model.ColumnInfo
import su.kidoz.core.model.ForeignKeyInfo
import su.kidoz.core.model.ForeignKeyRule
import su.kidoz.core.model.PrimaryKeyInfo
import su.kidoz.core.model.TableInfo
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramModelBuilderTest {
    @Test
    fun `build marks primary and foreign key columns`() {
        val model =
            DiagramModelBuilder.build(
                listOf(
                    TableMetadata(
                        table = TableInfo(name = "customers", schema = "public"),
                        columns =
                            listOf(
                                column("id", nullable = false),
                                column("email"),
                            ),
                        primaryKey = PrimaryKeyInfo(name = "customers_pkey", columns = listOf("id")),
                        foreignKeys = emptyList(),
                    ),
                    TableMetadata(
                        table = TableInfo(name = "orders", schema = "public"),
                        columns =
                            listOf(
                                column("id", nullable = false),
                                column("customer_id", nullable = false),
                            ),
                        primaryKey = PrimaryKeyInfo(name = "orders_pkey", columns = listOf("id")),
                        foreignKeys =
                            listOf(
                                ForeignKeyInfo(
                                    name = "orders_customer_id_fkey",
                                    columns = listOf("customer_id"),
                                    referencedTable = "customers",
                                    referencedColumns = listOf("id"),
                                ),
                            ),
                    ),
                ),
            )

        val orders = model.tables.single { it.name == "orders" }
        val customerId = orders.columns.single { it.name == "customer_id" }

        assertTrue(customerId.isForeignKey)
        assertTrue(orders.columns.single { it.name == "id" }.isPrimaryKey)
        assertEquals(1, model.relationships.size)
        assertEquals("orders_customer_id_fkey", model.relationships.single().name)
    }

    @Test
    fun `ddl generator emits draft table preview`() {
        val table =
            DiagramTable(
                id = "draft.table_1",
                name = "invoice",
                schema = "billing",
                x = 0f,
                y = 0f,
                columns =
                    listOf(
                        DiagramColumn(
                            id = "draft.table_1:id",
                            name = "id",
                            type = "INTEGER",
                            nullable = false,
                            isPrimaryKey = true,
                            isDraft = true,
                        ),
                        DiagramColumn(
                            id = "draft.table_1:amount",
                            name = "amount",
                            type = "DECIMAL(12,2)",
                            nullable = false,
                            isDraft = true,
                        ),
                    ),
                isDraft = true,
            )

        val ddl = DiagramDdlGenerator.generate(listOf(table), emptyList())

        assertTrue(ddl.contains("CREATE TABLE \"billing\".\"invoice\""))
        assertTrue(ddl.contains("\"amount\" DECIMAL(12,2) NOT NULL"))
        assertTrue(ddl.contains("PRIMARY KEY (\"id\")"))
    }

    @Test
    fun `ddl generator emits draft relationship preview`() {
        val customer = draftTable("customers", columnNames = listOf("id"))
        val order = draftTable("orders", columnNames = listOf("id", "customer_id"))
        val relationship =
            DiagramRelationship(
                id = "orders->customers",
                name = "fk_orders_customers",
                sourceTableId = order.id,
                sourceColumns = listOf("customer_id"),
                targetTableId = customer.id,
                targetColumns = listOf("id"),
                deleteRule = ForeignKeyRule.CASCADE,
                isDraft = true,
            )

        val ddl = DiagramDdlGenerator.generate(listOf(customer, order), listOf(relationship))

        assertTrue(ddl.contains("ALTER TABLE \"public\".\"orders\" ADD CONSTRAINT \"fk_orders_customers\""))
        assertTrue(ddl.contains("FOREIGN KEY (\"customer_id\") REFERENCES \"public\".\"customers\" (\"id\") ON DELETE CASCADE"))
    }

    @Test
    fun `draft validator rejects duplicate draft columns and missing relationship columns`() {
        val source =
            draftTable("orders", columnNames = listOf("id", "customer_id", "customer_id"))
        val target = draftTable("customers", columnNames = listOf("id"))
        val relationship =
            DiagramRelationship(
                id = "orders->customers",
                name = "fk_orders_customers",
                sourceTableId = source.id,
                sourceColumns = listOf("missing_customer_id"),
                targetTableId = target.id,
                targetColumns = listOf("id"),
                isDraft = true,
            )

        val issues = DiagramDraftValidator.validate(listOf(source, target), listOf(relationship))

        assertTrue(issues.any { it.contains("Duplicate column 'customer_id'") })
        assertTrue(issues.any { it.contains("missing column public.orders.missing_customer_id") })
    }

    private fun column(
        name: String,
        nullable: Boolean = true,
    ): ColumnInfo =
        ColumnInfo(
            name = name,
            dataType = "INTEGER",
            jdbcType = Types.INTEGER,
            nullable = nullable,
        )

    private fun draftTable(
        name: String,
        columnNames: List<String>,
    ): DiagramTable =
        DiagramTable(
            id = "public.$name",
            name = name,
            schema = "public",
            x = 0f,
            y = 0f,
            columns =
                columnNames.map { columnName ->
                    DiagramColumn(
                        id = "public.$name:$columnName",
                        name = columnName,
                        type = "INTEGER",
                        nullable = false,
                        isPrimaryKey = columnName == "id",
                        isDraft = true,
                    )
                },
            isDraft = true,
        )
}
