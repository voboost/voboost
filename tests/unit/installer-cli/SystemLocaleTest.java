package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SystemLocaleTest {
    private static final String BOOT = "11111111-1111-1111-1111-111111111111";
    private static String frame(String value) { return "locale-observation-v1\nbootId=" + BOOT + "\nconfig: " + value + "\nabi: arm64-v8a\n"; }
    @Test void readsPrimaryEnZhFromTraditionalAndBcp47Configurations() throws Exception {
        for (String value : new String[]{"en-rUS-ldltr-sw720dp", "mcc250-mnc1-en-rGB-land", "b+en+Latn+US-land"})
            assertEquals("en", SystemLocale.read(frame(value), BOOT).language());
        for (String value : new String[]{"zh-rCN-ldltr-land", "mcc460-mnc00-b+zh+Hans+CN-land", "zh-rCN,en-rUS-land"})
            assertEquals("zh", SystemLocale.read(frame(value), BOOT).language());
    }
    @Test void readsRussianOnlyFromTheCurrentActivityManagerPrimaryConfiguration()throws Exception {
        for(String value:new String[]{"ru-rRU-ldltr-land","b+ru+Cyrl+RU-land","mcc250-mnc1-ru-rRU,en-rUS-land"}) {
            var actual=SystemLocale.read(frame(value),BOOT);assertEquals("ru",actual.language());actual.requireOriginal(BOOT,"ru");
        }
        assertEquals("en",SystemLocale.read(frame("en-rUS,ru-rRU-land"),BOOT).language());
    }
    @Test void rejectsDifferentBootAmbiguousConfigurationAndUnsupportedPrimaryLanguage() {
        assertThrows(IOException.class, () -> SystemLocale.read(frame("en-rUS"), "22222222-2222-2222-2222-222222222222"));
        assertThrows(IOException.class, () -> SystemLocale.read(frame("en-rUS") + "config: zh-rCN\n", BOOT));
        for (String value : new String[]{"fr-rFR,en-rUS-land", "land-en-rUS", "english", "", "b+fr+Latn+FR-land"})
            assertThrows(IOException.class, () -> SystemLocale.read(frame(value), BOOT));
    }
    @Test void readOnlyReconciliationAcceptsOnlyTheActuallyRestoredOriginal() throws Exception {
        SystemLocale restored = SystemLocale.read(frame("en-rUS-land"), BOOT);
        restored.requireOriginal(BOOT, "en");
        assertThrows(IOException.class, () -> restored.requireOriginal(BOOT, "zh"));
        assertThrows(IOException.class, () -> restored.requireOriginal("22222222-2222-2222-2222-222222222222", "en"));
        assertThrows(IOException.class, () -> SystemLocale.read(frame("zh-rCN-land"), BOOT).requireOriginal(BOOT, "en"));
    }
    @Test void doesNotUseIndentedRecentConfigurationAsTheCurrentLocale() throws Exception {
        assertEquals("en", SystemLocale.read(frame("en-rUS") + "recent configs:\n  config: zh-rCN-land\n", BOOT).language());
        assertThrows(IOException.class, () -> SystemLocale.read("locale-observation-v1\nbootId=" + BOOT + "\nrecent configs:\n  config: en-rUS\n", BOOT));
    }
}
