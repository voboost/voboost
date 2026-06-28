package ru.voboost.ui.components

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import ru.voboost.StatusReader
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme
import ru.voboost.ui.LocaleManager

/**
 * Compose-style UI component for displaying daemon status.
 *
 * Shows:
 * - Daemon state (ready/degraded) with color indication
 * - Killed/panic flags
 * - List of injections with their states
 * - Graceful handling of null/unavailable status
 *
 * Layout: vertical LinearLayout with status header and injection list
 */
class DaemonStatusSection(
    private val context: Context,
    private val localeManager: LocaleManager,
) {
    private lateinit var theme: Theme
    private lateinit var language: Language

    // Status header elements
    private val statusHeader: TextView
    private val daemonVersionText: TextView
    private val daemonStateText: TextView
    private val flagsText: TextView

    // Injection list elements
    private val injectionList: LinearLayout

    // Container
    val container: LinearLayout

    init {
        // Create main container
        container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dpToPx(16f).toInt(),
                    dpToPx(12f).toInt(),
                    dpToPx(16f).toInt(),
                    dpToPx(12f).toInt(),
                )
            }

        // Create status header
        statusHeader =
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, dpToPx(8f).toInt())
            }
        container.addView(statusHeader)

        // Create daemon version text
        daemonVersionText =
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dpToPx(4f).toInt(), 0, 0, 0)
            }
        container.addView(daemonVersionText)

        // Create daemon state text
        daemonStateText =
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dpToPx(4f).toInt(), 0, 0, 0)
            }
        container.addView(daemonStateText)

        // Create flags text
        flagsText =
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dpToPx(4f).toInt(), 0, 0, dpToPx(8f).toInt())
            }
        container.addView(flagsText)

        // Create injection list
        injectionList =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(4f).toInt(), dpToPx(8f).toInt(), 0, 0)
            }
        container.addView(injectionList)
    }

    /**
     * Set theme for all UI elements.
     */
    fun setTheme(theme: Theme) {
        this.theme = theme

        // Apply theme colors
        val textColor = getThemeColor(theme)
        val backgroundColor = getThemeBackgroundColor(theme)

        container.setBackgroundColor(backgroundColor)

        statusHeader.setTextColor(textColor)
        daemonVersionText.setTextColor(textColor)
        daemonStateText.setTextColor(textColor)
        flagsText.setTextColor(textColor)
    }

    /**
     * Set language for all UI elements.
     */
    fun setLanguage(language: Language) {
        this.language = language
    }

    /**
     * Update UI with daemon status.
     *
     * @param status DaemonStatus or null if unavailable
     */
    fun updateStatus(status: StatusReader.DaemonStatus?) {
        if (status == null) {
            showUnavailable()
            return
        }

        // Show header
        statusHeader.text = localeManager.get("daemon_status_title", language)
        statusHeader.setTextColor(getThemeColor(theme))

        // Show daemon version
        daemonVersionText.text = "Daemon: ${status.daemon}"

        // Show state with color
        daemonStateText.text = "State: ${status.state.name}"
        daemonStateText.setTextColor(
            when (status.state) {
                StatusReader.DaemonState.READY -> Color.parseColor("#4CAF50") // Green
                StatusReader.DaemonState.DEGRADED -> Color.parseColor("#FFC107") // Amber
            },
        )

        // Show flags
        val flags = mutableListOf<String>()
        if (status.killed) flags.add("KILLED")
        if (status.panic) flags.add("PANIC")
        flagsText.text =
            if (flags.isNotEmpty()) {
                "Flags: ${flags.joinToString(", ")}"
            } else {
                ""
            }

        // Show injections
        injectionList.removeAllViews()
        if (status.injections.isEmpty()) {
            val noInjections =
                TextView(context).apply {
                    text = localeManager.get("daemon_injection_none", language)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(getThemeColor(theme))
                    setPadding(dpToPx(4f).toInt(), dpToPx(4f).toInt(), 0, 0)
                }
            injectionList.addView(noInjections)
        } else {
            status.injections.forEach { injection ->
                val injectionView = createInjectionView(injection)
                injectionList.addView(injectionView)
            }
        }
    }

    /**
     * Show unavailable status.
     */
    private fun showUnavailable() {
        statusHeader.text = localeManager.get("daemon_status_title", language)
        statusHeader.setTextColor(getThemeColor(theme))

        daemonVersionText.text = localeManager.get("daemon_status_unavailable", language)
        daemonStateText.text = ""
        flagsText.text = ""

        injectionList.removeAllViews()
        val unavailableView =
            TextView(context).apply {
                text = "Daemon not running or status file missing"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.GRAY)
                setPadding(dpToPx(4f).toInt(), dpToPx(4f).toInt(), 0, 0)
            }
        injectionList.addView(unavailableView)
    }

    /**
     * Create view for single injection.
     */
    private fun createInjectionView(injection: StatusReader.InjectionStatus): TextView {
        val stateColor =
            when (injection.state) {
                StatusReader.InjectionState.ACTIVE -> Color.parseColor("#4CAF50") // Green
                StatusReader.InjectionState.FAILED -> Color.parseColor("#F44336") // Red
                StatusReader.InjectionState.WAITING -> Color.parseColor("#FFC107") // Amber
                StatusReader.InjectionState.QUARANTINED ->
                    Color.parseColor(
                        "#FF5722",
                    ) // Deep Orange
                StatusReader.InjectionState.SKIPPED_COEXIST -> Color.parseColor("#9E9E9E") // Gray
            }

        return TextView(context).apply {
            text = "• ${injection.id}: ${injection.state.name} (${injection.process})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(stateColor)
            setPadding(dpToPx(4f).toInt(), dpToPx(2f).toInt(), 0, 0)
        }
    }

    /**
     * Get text color for theme.
     */
    private fun getThemeColor(theme: Theme): Int {
        return when (theme) {
            Theme.FREE_DARK, Theme.DREAMER_DARK -> Color.WHITE
            Theme.FREE_LIGHT, Theme.DREAMER_LIGHT -> Color.BLACK
        }
    }

    /**
     * Get background color for theme.
     */
    private fun getThemeBackgroundColor(theme: Theme): Int {
        return when (theme) {
            Theme.FREE_DARK, Theme.DREAMER_DARK -> Color.parseColor("#1E1E1E")
            Theme.FREE_LIGHT, Theme.DREAMER_LIGHT -> Color.parseColor("#F5F5F5")
        }
    }

    /**
     * Convert dp to px.
     */
    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
