package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class I18nSuiteRestorationTest {
    private static final class Port implements I18nSuiteRestoration.Port {
        String locale="ru"; boolean enabled=true, unknown; final List<String> calls=new ArrayList<>();
        public I18nSuiteRestoration.Observation observe(){calls.add("observe");return new I18nSuiteRestoration.Observation(locale,enabled,Path.of("/observed",Integer.toString(calls.size())));}
        public Path select(String value)throws Exception {calls.add("select:"+value);if(unknown)throw new IOException("unknown");locale=value;return Path.of("/selected");}
        public Path disable() {calls.add("disable");enabled=false;return Path.of("/disabled");}
    }
    @Test void restoresExactLocaleBeforeDisablingAndConfirmsEachEffect()throws Exception {
        Port port=new Port();var result=I18nSuiteRestoration.restore(new I18nSuiteRestoration.Observation("zh",false,Path.of("/initial")),port);
        assertEquals(List.of("observe","select:zh","observe","disable","observe"),port.calls);assertEquals("zh",result.current().locale());assertFalse(result.current().enabled());
    }
    @Test void unknownSelectionStopsBeforeDisableOrRetry() {
        Port port=new Port();port.unknown=true;assertThrows(IOException.class,()->I18nSuiteRestoration.restore(new I18nSuiteRestoration.Observation("en",false,Path.of("/initial")),port));assertEquals(List.of("observe","select:en"),port.calls);
    }
    @Test void cannotRestoreOriginalRussianIfOriginallyDisabledOrWhenSuiteAdmissionIsLost() {
        Port port=new Port();assertThrows(IOException.class,()->I18nSuiteRestoration.restore(new I18nSuiteRestoration.Observation("ru",false,Path.of("/initial")),port));assertTrue(port.calls.isEmpty());
        port.enabled=false;assertThrows(IOException.class,()->I18nSuiteRestoration.restore(new I18nSuiteRestoration.Observation("en",true,Path.of("/initial")),port));assertEquals(List.of("observe"),port.calls);
    }
    @Test void originalRussianEnabledNeedsReadbackWithoutMutations()throws Exception {
        Port port=new Port();var result=I18nSuiteRestoration.restore(new I18nSuiteRestoration.Observation("ru",true,Path.of("/initial")),port);assertEquals(List.of("observe"),port.calls);assertEquals("ru",result.current().locale());
    }
}
