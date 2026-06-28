package ru.voboost

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Robolectric provides a real org.json implementation (Android JSON stubs throw in plain JVM). */
@RunWith(RobolectricTestRunner::class)
class StatusReaderTest {
    private lateinit var testDir: File
    private lateinit var paths: Paths
    private lateinit var statusReader: StatusReader

    @Before
    fun setup() {
        // Create temporary directory for testing
        val testDirName = "voboost-test-${System.currentTimeMillis()}"
        testDir = File(System.getProperty("java.io.tmpdir"), testDirName)
        testDir.mkdirs()

        // Create test-specific paths implementation
        paths = TestPathsAndroid(testDir)
        statusReader = StatusReader(paths)
    }

    /**
     * Test-only paths implementation that doesn't require Android Context.
     */
    class TestPathsAndroid(private val baseDir: File) : Paths {
        override val appZone: File get() = baseDir
        override val injectJson: File get() = File(appZone, "inject.json")
        override val injectStatusJson: File get() = File(appZone, "inject-status.json")
        override val stagingDir: File get() = File(appZone, "staging").also { it.mkdirs() }
        override val configFile: File get() = File(appZone, "config.yaml")
        override val logsDir: File get() = File(appZone, "logs").also { it.mkdirs() }
        override val scriptsDirectory: File get() = File(appZone, "scripts").also { it.mkdirs() }
    }

    @After
    fun cleanup() {
        // Clean up temporary directory
        testDir.deleteRecursively()
    }

    @Test
    fun testReadBasicStatus() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "test-agent",
                        "process": "com.test.app",
                        "state": "active"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        val status = statusReader.read()

        assertNotNull("Status should be read successfully", status)
        assertEquals("voboost-inject 1.0.0", status!!.daemon)
        assertEquals(1, status.manifest)
        assertEquals(StatusReader.DaemonState.READY, status.state)
        assertFalse(status.killed)
        assertFalse(status.panic)
        assertEquals(1, status.injections.size)

        val injection = status.injections[0]
        assertEquals("test-agent", injection.id)
        assertEquals("com.test.app", injection.process)
        assertEquals(StatusReader.InjectionState.ACTIVE, injection.state)
    }

    @Test
    fun testReadDegradedStatus() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "degraded",
                "killed": false,
                "panic": false,
                "injections": []
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        val status = statusReader.read()

        assertNotNull("Status should be read successfully", status)
        assertEquals(StatusReader.DaemonState.DEGRADED, status!!.state)
        assertTrue(status.injections.isEmpty())
    }

    @Test
    fun testReadStatusWithKilledFlag() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": true,
                "panic": false,
                "injections": []
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        val status = statusReader.read()

        assertNotNull("Status should be read successfully", status)
        assertTrue(status!!.killed)
    }

    @Test
    fun testReadStatusWithPanicFlag() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "degraded",
                "killed": false,
                "panic": true,
                "injections": []
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        val status = statusReader.read()

        assertNotNull("Status should be read successfully", status)
        assertTrue(status!!.panic)
    }

    @Test
    fun testReadStatusWithMultipleInjections() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "agent-1",
                        "process": "com.test.app1",
                        "state": "active"
                    },
                    {
                        "id": "agent-2",
                        "process": "com.test.app2",
                        "state": "failed"
                    },
                    {
                        "id": "agent-3",
                        "process": "com.test.app3",
                        "state": "waiting"
                    },
                    {
                        "id": "agent-4",
                        "process": "com.test.app4",
                        "state": "quarantined"
                    },
                    {
                        "id": "agent-5",
                        "process": "com.test.app5",
                        "state": "skipped-coexist"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        val status = statusReader.read()

        assertNotNull("Status should be read successfully", status)
        assertEquals(5, status!!.injections.size)

        assertEquals("agent-1", status.injections[0].id)
        assertEquals(StatusReader.InjectionState.ACTIVE, status.injections[0].state)

        assertEquals("agent-2", status.injections[1].id)
        assertEquals(StatusReader.InjectionState.FAILED, status.injections[1].state)

        assertEquals("agent-3", status.injections[2].id)
        assertEquals(StatusReader.InjectionState.WAITING, status.injections[2].state)

        assertEquals("agent-4", status.injections[3].id)
        assertEquals(StatusReader.InjectionState.QUARANTINED, status.injections[3].state)

        assertEquals("agent-5", status.injections[4].id)
        assertEquals(StatusReader.InjectionState.SKIPPED_COEXIST, status.injections[4].state)
    }

    @Test
    fun testReadStatusWithPartialWrite() {
        // Write incomplete JSON (simulating in-flight write)
        val incompleteJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "test-agent",
                        "process": "com.test.app",
                        "state": "active"
                    }
            """.trimIndent()

        paths.injectStatusJson.writeText(incompleteJson)

        val status = statusReader.read()

        // Should return null for partial/in-flight write
        assertNull("Status should be null for partial write", status)
    }

    @Test
    fun testReadStatusWithNonExistentFile() {
        // Don't create the status file

        val status = statusReader.read()

        // Should return null when file doesn't exist
        assertNull("Status should be null when file doesn't exist", status)
    }

    @Test
    fun testReadStatusWithInvalidJson() {
        // Write invalid JSON
        val invalidJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "test-agent",
                        "process": "com.test.app",
                        "state": "active"
                    }
                ]
            """.trimIndent()

        paths.injectStatusJson.writeText(invalidJson)

        val status = statusReader.read()

        // Should return null for invalid JSON
        assertNull("Status should be null for invalid JSON", status)
    }

    @Test
    fun testIsReadyWithActiveInjections() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "test-agent",
                        "process": "com.test.app",
                        "state": "active"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        assertTrue(statusReader.isReadyWithActiveInjections())
    }

    @Test
    fun testIsReadyWithActiveInjectionsDegradedState() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "degraded",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "test-agent",
                        "process": "com.test.app",
                        "state": "active"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        assertFalse(statusReader.isReadyWithActiveInjections())
    }

    @Test
    fun testIsReadyWithActiveInjectionsNoActiveInjections() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "test-agent",
                        "process": "com.test.app",
                        "state": "failed"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        assertFalse(statusReader.isReadyWithActiveInjections())
    }

    @Test
    fun testGetActiveInjectionCount() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "agent-1",
                        "process": "com.test.app1",
                        "state": "active"
                    },
                    {
                        "id": "agent-2",
                        "process": "com.test.app2",
                        "state": "active"
                    },
                    {
                        "id": "agent-3",
                        "process": "com.test.app3",
                        "state": "failed"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        assertEquals(2, statusReader.getActiveInjectionCount())
    }

    @Test
    fun testGetFailedInjectionIds() {
        val statusJson =
            """
            {
                "daemon": "voboost-inject 1.0.0",
                "manifest": 1,
                "state": "ready",
                "killed": false,
                "panic": false,
                "injections": [
                    {
                        "id": "agent-1",
                        "process": "com.test.app1",
                        "state": "active"
                    },
                    {
                        "id": "agent-2",
                        "process": "com.test.app2",
                        "state": "failed"
                    },
                    {
                        "id": "agent-3",
                        "process": "com.test.app3",
                        "state": "failed"
                    }
                ]
            }
            """.trimIndent()

        paths.injectStatusJson.writeText(statusJson)

        val failedIds = statusReader.getFailedInjectionIds()
        assertEquals(2, failedIds.size)
        assertTrue(failedIds.contains("agent-2"))
        assertTrue(failedIds.contains("agent-3"))
    }

    @Test
    fun testGetFailedInjectionIdsNoStatus() {
        // Don't create status file

        val failedIds = statusReader.getFailedInjectionIds()
        assertTrue(failedIds.isEmpty())
    }
}
