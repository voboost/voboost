package ru.voboost

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LoggerTest {
    private lateinit var testDir: File
    private lateinit var logger: Logger

    @Before
    fun setUp() {
        // Create unique test directory for each test
        testDir = File(System.getProperty("java.io.tmpdir"), "voboost_test_${UUID.randomUUID()}")
        testDir.mkdirs()

        // Create fresh logger instance for each test
        logger = Logger.create(testDir, "debug", 7)
    }

    @After
    fun tearDown() {
        // Clean up after each test
        logger.shutdown()
        testDir.deleteRecursively()
    }

    // === Level Tests ===

    @Test
    fun `level priority ordering is correct`() {
        assertTrue(Logger.Level.NONE.priority < Logger.Level.ERROR.priority)
        assertTrue(Logger.Level.ERROR.priority < Logger.Level.INFO.priority)
        assertTrue(Logger.Level.INFO.priority < Logger.Level.DEBUG.priority)
    }

    @Test
    fun `level tags are correct`() {
        assertEquals("[-]", Logger.Level.ERROR.tag)
        assertEquals("[+]", Logger.Level.INFO.tag)
        assertEquals("[*]", Logger.Level.DEBUG.tag)
        assertEquals("", Logger.Level.NONE.tag)
    }

    @Test
    fun `level valueOf valid strings`() {
        assertEquals(Logger.Level.NONE, Logger.Level.valueOf("NONE"))
        assertEquals(Logger.Level.ERROR, Logger.Level.valueOf("ERROR"))
        assertEquals(Logger.Level.INFO, Logger.Level.valueOf("INFO"))
        assertEquals(Logger.Level.DEBUG, Logger.Level.valueOf("DEBUG"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `level valueOf invalid string throws exception`() {
        Logger.Level.valueOf("INVALID")
    }

    // === Instance Method Tests ===

    @Test
    fun `logger instance methods work correctly`() {
        logger.logError("test", "error message")
        logger.logInfo("test", "info message")
        logger.logDebug("test", "debug message")

        // Wait for async processing
        Thread.sleep(100)

        // Verify log files exist
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        assertTrue("Log files should exist", logFiles?.isNotEmpty() == true)
    }

    @Test
    fun `logger level filtering works`() {
        // Change to ERROR level
        logger.setLogLevel("error")

        logger.logError("test", "error message")
        logger.logInfo("test", "info message") // Should be filtered
        logger.logDebug("test", "debug message") // Should be filtered

        Thread.sleep(100)

        // Check log content
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        assertTrue("Should contain error message", logContent.contains("error message"))
        assertFalse("Should not contain info message", logContent.contains("info message"))
        assertFalse("Should not contain debug message", logContent.contains("debug message"))
    }

    @Test
    fun `logger runtime level change works`() {
        // Set to ERROR level
        logger.setLogLevel("error")
        logger.logInfo("test", "should not appear")

        // Change to INFO level
        logger.setLogLevel("info")
        logger.logInfo("test", "should appear")

        Thread.sleep(100)

        // Check log content
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        assertFalse("Should not contain first message", logContent.contains("should not appear"))
        assertTrue("Should contain second message", logContent.contains("should appear"))
    }

    @Test
    fun `logger handles special characters`() {
        val specialMessage = "Special chars: <>&\"'\\n\\t and unicode: 日本语 中文 한국어 🎉"

        logger.logError("test", specialMessage)
        logger.logInfo("test", specialMessage)
        logger.logDebug("test", specialMessage)

        Thread.sleep(100)

        // Verify log files exist and contain content
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        assertTrue("Log files should exist", logFiles?.isNotEmpty() == true)

        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""
        assertTrue("Should contain special characters", logContent.contains("日本语"))
    }

    @Test
    fun `logger handles very long messages`() {
        val longMessage = "A".repeat(1000)

        logger.logError("test", longMessage)
        logger.logInfo("test", longMessage)
        logger.logDebug("test", longMessage)

        Thread.sleep(100)

        // Verify log files exist
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        assertTrue("Log files should exist", logFiles?.isNotEmpty() == true)
    }

    @Test
    fun `logger different sources work`() {
        logger.logError("source1", "error from source1")
        logger.logInfo("source2", "info from source2")
        logger.logDebug("source3", "debug from source3")

        Thread.sleep(100)

        // Verify all sources are logged
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        assertTrue("Should contain source1", logContent.contains("source1"))
        assertTrue("Should contain source2", logContent.contains("source2"))
        assertTrue("Should contain source3", logContent.contains("source3"))
    }

    @Test
    fun `logger concurrent logging works`() {
        val threadCount = 3
        val messagesPerThread = 5
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) { threadId ->
            Thread {
                repeat(messagesPerThread) { msgId ->
                    logger.logInfo("thread-$threadId", "message-$msgId")
                }
                latch.countDown()
            }.start()
        }

        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(200)

        // Verify all messages were logged
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        assertTrue("Log files should exist", logFiles?.isNotEmpty() == true)

        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        // Check that we have messages from all threads
        repeat(threadCount) { threadId ->
            assertTrue(
                "Should contain messages from thread $threadId",
                logContent.contains("thread-$threadId"),
            )
        }
    }

    // === Raw Logging Tests ===

    @Test
    fun `raw method writes line without adding timestamp`() {
        val preformattedLine = "2024-12-15 14:30:45.123 [+] test-source: test message"

        logger.logRaw(preformattedLine)

        Thread.sleep(100)

        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        // Should contain exact line (no double timestamp)
        assertTrue("Should contain preformatted line", logContent.contains(preformattedLine))

        // Should have exactly one timestamp in the line
        val lines = logContent.trim().split("\n")
        val rawLine = lines.find { it.contains("test-source") }
        assertTrue("Raw line should exist", rawLine != null)

        // Count timestamps (YYYY-MM-DD pattern)
        val timestampCount = rawLine!!.split(Regex("\\d{4}-\\d{2}-\\d{2}")).size - 1
        assertEquals("Should have exactly one timestamp", 1, timestampCount)
    }

    @Test
    fun `raw method handles multiple lines`() {
        logger.logRaw("2024-12-15 14:30:45.123 [+] source1: message1")
        logger.logRaw("2024-12-15 14:30:45.456 [*] source2: message2")
        logger.logRaw("2024-12-15 14:30:45.789 [-] source3: message3")

        Thread.sleep(100)

        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        assertTrue("Should contain message1", logContent.contains("message1"))
        assertTrue("Should contain message2", logContent.contains("message2"))
        assertTrue("Should contain message3", logContent.contains("message3"))
    }

    @Test
    fun `raw method works concurrently with regular logging`() {
        val threadCount = 3
        val latch = CountDownLatch(threadCount)

        // Thread 1: Regular logging
        Thread {
            repeat(5) { i ->
                logger.logInfo("regular", "message-$i")
            }
            latch.countDown()
        }.start()

        // Thread 2: Raw logging
        Thread {
            repeat(5) { i ->
                logger.logRaw("2024-12-15 14:30:45.${i}00 [+] raw: message-$i")
            }
            latch.countDown()
        }.start()

        // Thread 3: Mixed logging
        Thread {
            repeat(5) { i ->
                if (i % 2 == 0) {
                    logger.logDebug("mixed", "message-$i")
                } else {
                    logger.logRaw("2024-12-15 14:30:46.${i}00 [*] raw-mixed: message-$i")
                }
            }
            latch.countDown()
        }.start()

        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(200)

        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        // Verify all types of messages present
        assertTrue("Should contain regular messages", logContent.contains("regular"))
        assertTrue("Should contain raw messages", logContent.contains("raw:"))
        assertTrue("Should contain mixed messages", logContent.contains("mixed"))
    }

    @Test
    fun `static raw method works after init`() {
        // Reset any existing instance
        Logger.shutdown()

        // Mock Android Context
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(testDir)

        // Initialize logger
        Logger.init(mockContext, "debug", 7)

        // Test raw logging
        Logger.raw("2024-12-15 14:30:45.123 [+] test: raw message")

        Thread.sleep(100)

        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        val logContent = logFiles?.joinToString("\n") { it.readText() } ?: ""

        assertTrue("Should contain raw message", logContent.contains("raw message"))

        // Clean up
        Logger.shutdown()
    }

    // === Static Method Tests (Backward Compatibility) ===

    @Test
    fun `static getLevel returns default level before init`() {
        // Reset any existing instance
        Logger.shutdown()

        val level = Logger.getLevel()
        assertTrue(level in listOf("none", "error", "info", "debug"))
    }

    @Test
    fun `static setLevel works without init`() {
        // Reset any existing instance
        Logger.shutdown()

        // When no instance is initialized, setLevel doesn't persist
        // and getLevel returns the default "info"
        Logger.setLevel("debug")
        assertEquals("info", Logger.getLevel()) // Default when no instance

        Logger.setLevel("invalid")
        assertEquals("info", Logger.getLevel()) // Still default
    }

    @Test
    fun `static logging methods do not crash without init`() {
        // Reset any existing instance
        Logger.shutdown()

        Logger.error("test", "error message")
        Logger.info("test", "info message")
        Logger.debug("test", "debug message")
        assertTrue(true)
    }

    @Test
    fun `static logging methods handle empty strings`() {
        // Reset any existing instance
        Logger.shutdown()

        Logger.error("", "")
        Logger.info("", "")
        Logger.debug("", "")
        assertTrue(true)
    }

    @Test
    fun `static initialize with context works`() {
        // Reset any existing instance
        Logger.shutdown()

        // Mock Android Context
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(testDir)

        // Initialize logger
        Logger.init(mockContext, "debug", 7)

        // Test that initialization works
        assertEquals("debug", Logger.getLevel())

        // Test logging
        Logger.error("test", "error message")
        Logger.info("test", "info message")
        Logger.debug("test", "debug message")

        Thread.sleep(100)

        // Verify log files exist
        val logFiles =
            testDir.listFiles()?.filter {
                it.name.startsWith("voboost-") && it.name.endsWith(".log")
            }
        assertTrue("Log files should exist", logFiles?.isNotEmpty() == true)

        // Clean up
        Logger.shutdown()
    }

    @Test
    fun `multiple logger instances are independent`() {
        // Create second logger with different directory
        val testDir2 =
            File(System.getProperty("java.io.tmpdir"), "voboost_test2_${UUID.randomUUID()}")
        testDir2.mkdirs()

        val logger2 = Logger.create(testDir2, "error", 7)

        try {
            // Set different levels
            logger.setLogLevel("debug")
            logger2.setLogLevel("error")

            // Log to both
            logger.logInfo("test", "info message")
            logger2.logInfo("test", "info message")

            Thread.sleep(100)

            // Check first logger has the message
            val logFiles1 =
                testDir.listFiles()?.filter {
                    it.name.startsWith("voboost-") && it.name.endsWith(".log")
                }
            val logContent1 = logFiles1?.joinToString("\n") { it.readText() } ?: ""
            assertTrue("Logger1 should contain info message", logContent1.contains("info message"))

            // Check second logger doesn't have the message (filtered by ERROR level)
            val logFiles2 =
                testDir2.listFiles()?.filter {
                    it.name.startsWith("voboost-") && it.name.endsWith(".log")
                }
            val logContent2 = logFiles2?.joinToString("\n") { it.readText() } ?: ""
            assertFalse(
                "Logger2 should not contain info message",
                logContent2.contains("info message"),
            )
        } finally {
            logger2.shutdown()
            testDir2.deleteRecursively()
        }
    }
}
