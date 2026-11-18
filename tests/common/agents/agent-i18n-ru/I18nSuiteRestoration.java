package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Restore the independently observed original locale and desired state after the admitted suite. */
public final class I18nSuiteRestoration {
    public record Observation(String locale, boolean enabled, Path evidence) {}
    public record Proof(Observation current, List<Path> evidence) {
        public Proof { evidence = List.copyOf(evidence); }
    }
    public interface Port {
        /** Independent, current boot/session-fenced picker, system locale and persisted desired readback. */
        Observation observe() throws Exception;
        /** Select once and retain actual effect evidence. */
        Path select(String locale) throws Exception;
        /** Disable once and retain actual desired and all-owner stop evidence. */
        Path disable() throws Exception;
    }
    public static Proof restore(Observation original, Port port) throws Exception {
        require(original);
        if (original.locale().equals("ru") && !original.enabled()) throw new IOException("original Russian requires enabled contribution");
        List<Path> evidence = new ArrayList<>();
        evidence.add(original.evidence());
        Observation current = port.observe(); require(current); evidence.add(current.evidence());
        if (!current.enabled()) throw new IOException("suite's current enabled contribution is unavailable");
        if (!current.locale().equals(original.locale())) {
            evidence.add(required(port.select(original.locale())));
            current = port.observe(); require(current); evidence.add(current.evidence());
            if (!current.enabled() || !current.locale().equals(original.locale())) throw new IOException("restored locale readback differs");
        }
        if (!original.enabled()) {
            evidence.add(required(port.disable()));
            current = port.observe(); require(current); evidence.add(current.evidence());
        }
        if (!current.locale().equals(original.locale()) || current.enabled() != original.enabled()) throw new IOException("original locale or desired state was not restored");
        return new Proof(current, evidence);
    }
    private static void require(Observation observation) throws IOException {
        if (observation == null || observation.locale() == null || !Set.of("en", "zh", "ru").contains(observation.locale()) || observation.evidence() == null)
            throw new IOException("actual retained locale and desired state observation required");
    }
    private static Path required(Path path) throws IOException {
        if (path == null) throw new IOException("actual retained operation evidence required");
        return path;
    }
    private I18nSuiteRestoration() {}
}
