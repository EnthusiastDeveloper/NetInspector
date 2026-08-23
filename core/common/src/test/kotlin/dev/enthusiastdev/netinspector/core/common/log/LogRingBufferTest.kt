package dev.enthusiastdev.netinspector.core.common.log

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test

private fun entry(
    n: Int,
    priority: Int = 4,
) = LogEntry(timestampMillis = n.toLong(), priority = priority, tag = "Tag", message = "msg$n")

class LogRingBufferTest {
    @Test
    fun `keeps everything under capacity`() {
        val buffer = LogRingBuffer(capacity = 5)
        repeat(3) { buffer.add(entry(it)) }

        assertThat(buffer.snapshot()).hasSize(3)
    }

    @Test
    fun `evicts oldest entries beyond capacity, keeping arrival order`() {
        val buffer = LogRingBuffer(capacity = 3)
        repeat(5) { buffer.add(entry(it)) }

        val snapshot = buffer.snapshot()
        assertThat(snapshot).hasSize(3)
        assertThat(snapshot.map { it.timestampMillis }).containsExactly(2L, 3L, 4L).inOrder()
    }

    @Test
    fun `concurrent adds never exceed capacity`() =
        runBlocking {
            val buffer = LogRingBuffer(capacity = 50)
            val jobs = (0 until 500).map { i -> async(Dispatchers.Default) { buffer.add(entry(i)) } }
            jobs.forEach { it.await() }

            assertThat(buffer.snapshot()).hasSize(50)
        }

    @Test
    fun `toLine formats every priority level`() {
        assertThat(entry(1, priority = 2).toLine()).isEqualTo("1 V/Tag: msg1")
        assertThat(entry(1, priority = 3).toLine()).isEqualTo("1 D/Tag: msg1")
        assertThat(entry(1, priority = 4).toLine()).isEqualTo("1 I/Tag: msg1")
        assertThat(entry(1, priority = 5).toLine()).isEqualTo("1 W/Tag: msg1")
        assertThat(entry(1, priority = 6).toLine()).isEqualTo("1 E/Tag: msg1")
    }

    @Test
    fun `toLine falls back to a dash for a null tag`() {
        assertThat(LogEntry(1, 4, null, "msg").toLine()).isEqualTo("1 I/-: msg")
    }
}
