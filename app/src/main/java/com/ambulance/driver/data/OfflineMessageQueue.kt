package com.ambulance.driver.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class QueuedMessage(
    val topic: String,
    val payload: String
)

/**
 * Append-only JSONL queue so GPS publishes survive MQTT disconnects.
 */
class OfflineMessageQueue(context: Context) {

    private val file = File(context.filesDir, "mqtt_offline_queue.jsonl")
    private val lock = Any()
    private val count = AtomicInteger(readCountUnlocked())

    fun size(): Int = count.get()

    fun enqueue(topic: String, payload: String) {
        val line = JSONObject()
            .put("topic", topic)
            .put("payload", payload)
            .toString()
        synchronized(lock) {
            file.appendText(line + "\n")
            count.incrementAndGet()
        }
    }

    fun snapshot(): List<QueuedMessage> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching {
                        val obj = JSONObject(line)
                        QueuedMessage(
                            topic = obj.getString("topic"),
                            payload = obj.getString("payload")
                        )
                    }.getOrNull()
                }
        }
    }

    fun replaceAll(remaining: List<QueuedMessage>) {
        synchronized(lock) {
            if (remaining.isEmpty()) {
                if (file.exists()) file.delete()
                count.set(0)
                return
            }
            file.writeText(
                remaining.joinToString(separator = "\n", postfix = "\n") { item ->
                    JSONObject()
                        .put("topic", item.topic)
                        .put("payload", item.payload)
                        .toString()
                }
            )
            count.set(remaining.size)
        }
    }

    private fun readCountUnlocked(): Int {
        if (!file.exists()) return 0
        return file.readLines().count { it.isNotBlank() }
    }
}
