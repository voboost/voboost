package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class ObservationRequestTest {
    @Test void observationsUseOnlyTheClosedReadOperationsAndNeverCarryWrites() throws Exception {
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> {
            var request = RunFiles.parse(bytes);
            assertTrue(List.of("multimedia.system", "vehicle.can.get", "multimedia.phone.state", "vehicle.get").contains(request.get("op").getAsString()));
            assertFalse(request.has("values"));
            assertFalse(request.has("payload"));
            return new VoyemuClient.ProcessResult(1, false, "", "observation unavailable");
        });
        VoyemuClient.Session session = VoyemuClient.Session.attached("a".repeat(32));
        for (VoyemuClient.Attempt request : List.of(client.systemRead("sys", "profile", session, 1000),
                client.canRead("can", "profile", session, 5000), client.phoneRead("phone", "profile", session),
                client.vehicleRead("vehicle", "profile", session))) {
            assertEquals(VoyemuClient.State.UNKNOWN, client.execute(request, Duration.ofSeconds(1)).state());
        }
        assertThrows(IllegalArgumentException.class, () -> client.canRead("bad", "profile", session, 0));
        assertThrows(IllegalArgumentException.class, () -> client.canRead("bad", "profile", session, 10001));
        assertThrows(IllegalArgumentException.class, () -> client.systemRead("bad", "profile", session, -1));
        assertThrows(IllegalArgumentException.class, () -> client.systemRead("bad", "profile", session, 60001));
    }
}
