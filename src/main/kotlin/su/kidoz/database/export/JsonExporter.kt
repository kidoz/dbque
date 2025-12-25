package su.kidoz.database.export

import kotlinx.serialization.json.*
import su.kidoz.core.model.ResultColumn

class JsonExporter {
    companion object {
        private val prettyJson = Json { prettyPrint = true }
    }

    fun export(
        columns: List<ResultColumn>,
        rows: List<List<Any?>>,
    ): String {
        val jsonArray =
            buildJsonArray {
                rows.forEach { row ->
                    add(
                        buildJsonObject {
                            columns.forEachIndexed { index, column ->
                                val value = row.getOrNull(index)
                                put(column.label, valueToJsonElement(value))
                            }
                        },
                    )
                }
            }

        return prettyJson.encodeToString(JsonArray.serializer(), jsonArray)
    }

    private fun valueToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is ByteArray -> JsonPrimitive("[BINARY]")
            else -> JsonPrimitive(value.toString())
        }
}
