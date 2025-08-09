package ru.voboost.ui.components

import kotlinx.coroutines.flow.Flow

/**
 * Base class for all panel elements
 */
sealed class AbstractControl {
    /**
     * Unique identifier for this element
     */
    abstract val id: String

    /**
     * Reactive visibility state
     */
    abstract val visibility: Flow<Boolean>

    /**
     * Check if element is currently visible
     */
    suspend fun isVisible(): Boolean {
        var visible = true
        visibility.collect { visible = it }
        return visible
    }
}
