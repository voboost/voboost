package ru.voboost.feature

/**
 * Abstract base for native (non-Frida) features.
 * For features that work at Android level without Frida injection.
 */
abstract class FeatureNative : Feature {
    // No Frida-specific logic
    // Subclasses implement enable/disable with native Android APIs
}
