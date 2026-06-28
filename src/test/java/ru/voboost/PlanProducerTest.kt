package ru.voboost

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voboost.config.models.CarModel
import ru.voboost.config.models.Config
import ru.voboost.config.models.PedestrianWarning
import ru.voboost.config.models.StartupMode
import ru.voboost.config.models.Tab
import java.io.File

/**
 * PlanProducer tests. Robolectric provides a real org.json implementation
 * (the Android JSON stubs throw "not mocked" in a plain JVM unit test).
 */
@RunWith(RobolectricTestRunner::class)
class PlanProducerTest {
    private lateinit var testDir: File
    private lateinit var paths: Paths
    private lateinit var planProducer: PlanProducer

    @Before
    fun setup() {
        val testDirName = "voboost-test-${System.currentTimeMillis()}"
        testDir = File(System.getProperty("java.io.tmpdir"), testDirName)
        testDir.mkdirs()
        paths = TestPaths(testDir)
        planProducer = PlanProducer(paths)
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    private fun makeConfig(startup: StartupMode = StartupMode.`interface`): Config =
        Config(
            settingsLanguage = "en",
            settingsTheme = "dark",
            settingsCarModel = CarModel.free,
            settingsInterfaceShiftX = 145,
            settingsInterfaceShiftY = 50,
            settingsActiveTab = Tab.settings,
            settingsStartup = startup,
            vehiclePedestrianWarning = PedestrianWarning.original,
        )

    @Test
    fun testProduceBasicPlan() {
        val agents =
            listOf(PlanProducer.AgentEntry(id = "test-agent", config = mapOf("key" to "value")))
        val result = planProducer.produce(makeConfig(), agents, disabled = false)

        assertTrue("Plan production should succeed", result.isSuccess)
        assertTrue("Plan file should exist", paths.injectJson.exists())

        val planJson = JSONObject(paths.injectJson.readText())
        assertEquals(0, planJson.getInt("version"))
        assertEquals("interface", planJson.getString("startup"))
        assertFalse(planJson.getBoolean("disabled"))

        val agentsArray = planJson.getJSONArray("agents")
        assertEquals(1, agentsArray.length())
        val agentJson = agentsArray.getJSONObject(0)
        assertEquals("test-agent", agentJson.getString("id"))
        assertTrue(agentJson.getBoolean("enabled"))
        assertEquals("value", agentJson.getJSONObject("config").getString("key"))
    }

    @Test
    fun testProducePlanWithStartupOff() {
        val result =
            planProducer.produce(
                makeConfig(StartupMode.off),
                emptyList(),
                disabled = false,
            )

        assertTrue("Plan production should succeed", result.isSuccess)
        val planJson = JSONObject(paths.injectJson.readText())
        assertEquals("none", planJson.getString("startup"))
    }

    @Test
    fun testProducePlanWithKillSwitch() {
        val result =
            planProducer.produce(
                makeConfig(),
                listOf(PlanProducer.AgentEntry(id = "test-agent")),
                disabled = true,
            )
        assertTrue("Plan production should succeed", result.isSuccess)
        val planJson = JSONObject(paths.injectJson.readText())
        assertTrue(planJson.getBoolean("disabled"))
    }

    @Test
    fun testProducePlanWithMultipleAgents() {
        val agents =
            listOf(
                PlanProducer.AgentEntry(id = "agent-1", config = mapOf("key1" to "value1")),
                PlanProducer.AgentEntry(id = "agent-2", enabled = false),
                PlanProducer.AgentEntry(
                    id = "agent-3",
                    config = mapOf("key2" to "value2", "key3" to "value3"),
                ),
            )
        planProducer.produce(makeConfig(), agents, disabled = false)

        val agentsArray = JSONObject(paths.injectJson.readText()).getJSONArray("agents")
        assertEquals(3, agentsArray.length())

        assertEquals("agent-1", agentsArray.getJSONObject(0).getString("id"))
        assertTrue(agentsArray.getJSONObject(0).getBoolean("enabled"))
        assertEquals(
            "value1",
            agentsArray.getJSONObject(0).getJSONObject("config").getString("key1"),
        )

        assertEquals("agent-2", agentsArray.getJSONObject(1).getString("id"))
        assertFalse(agentsArray.getJSONObject(1).getBoolean("enabled"))

        assertEquals("agent-3", agentsArray.getJSONObject(2).getString("id"))
        assertEquals(
            "value3",
            agentsArray.getJSONObject(2).getJSONObject("config").getString("key3"),
        )
    }

    @Test
    fun testProducePlanSizeLimit() {
        // 15 000 keys x ~110 bytes each ~= 1.6 MiB > 1 MiB plan cap.
        val largeConfig = (1..15_000).associate { "key$it" to "x".repeat(100) }
        val result =
            planProducer.produce(
                makeConfig(),
                listOf(PlanProducer.AgentEntry(id = "large-agent", config = largeConfig)),
                disabled = false,
            )
        assertTrue("Oversized plan should fail", result.isFailure)
        assertFalse("Plan file should not exist when oversized", paths.injectJson.exists())
    }

    @Test
    fun testProduceAgentConfigSizeLimit() {
        // 7 000 keys x ~18 bytes each ~= 126 KiB > 64 KiB per-agent config cap.
        val largeConfig = (1..7_000).associate { "key$it" to "x".repeat(10) }
        val result =
            planProducer.produce(
                makeConfig(),
                listOf(PlanProducer.AgentEntry(id = "large-agent", config = largeConfig)),
                disabled = false,
            )
        assertTrue("Oversized agent config should fail", result.isFailure)
        assertFalse("Plan file should not exist when config oversized", paths.injectJson.exists())
    }

    @Test
    fun testAtomicWrite() {
        val result =
            planProducer.produce(
                makeConfig(),
                listOf(PlanProducer.AgentEntry(id = "test-agent")),
                disabled = false,
            )
        assertTrue("Plan production should succeed", result.isSuccess)
        val tempFile = File(paths.injectJson.parent, ".${paths.injectJson.name}.tmp")
        assertFalse("Temp file should be cleaned up after write", tempFile.exists())
    }

    @Test
    fun testRemovePlan() {
        planProducer.produce(makeConfig(), listOf(PlanProducer.AgentEntry(id = "test-agent")))
        assertTrue("Plan file should exist", paths.injectJson.exists())

        assertTrue("Plan removal should succeed", planProducer.removePlan().isSuccess)
        assertFalse("Plan file should not exist after removal", paths.injectJson.exists())

        assertTrue("Removing non-existent plan should succeed", planProducer.removePlan().isSuccess)
    }
}

/** Test-only Paths implementation that does not require an Android Context. */
class TestPaths(private val baseDir: File) : Paths {
    override val appZone: File get() = baseDir
    override val injectJson: File get() = File(appZone, "inject.json")
    override val injectStatusJson: File get() = File(appZone, "inject-status.json")
    override val stagingDir: File get() = File(appZone, "staging").also { it.mkdirs() }
    override val configFile: File get() = File(appZone, "config.yaml")
    override val logsDir: File get() = File(appZone, "logs").also { it.mkdirs() }
    override val scriptsDirectory: File get() = File(appZone, "scripts").also { it.mkdirs() }
}
