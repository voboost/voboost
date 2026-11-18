package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JavaStopAcknowledgementTest {

    private static final String BOOT = "11111111-1111-1111-1111-111111111111",
            PUBLISHER = BOOT + ":43:400";

    private static final RootVehicleState.Authority AUTHORITY = new RootVehicleState.Authority(BOOT,
            "generation", "catalog", "com.qinggan.systemservice", "41", "514", "8", "17");

    private static JsonObject binding(String document, String sequence) {
        JsonObject value = new JsonObject();
        value.addProperty("targetPid", "41");
        value.addProperty("targetStarttime", "514");
        value.addProperty("endpointEpoch", "8");
        value.addProperty("generationId", "generation");
        value.addProperty("catalogSha256", "catalog");
        value.addProperty("desiredDocumentGeneration", document);
        JsonObject accepted = new JsonObject(), receipt = new JsonObject();
        accepted.addProperty("documentGeneration", document);
        receipt.addProperty("sequence", sequence);
        receipt.addProperty("daemonPid", "43");
        receipt.addProperty("daemonStarttime", "400");
        receipt.addProperty("bootId", BOOT);
        accepted.add("receipt", receipt);
        value.add("acceptedStatus", accepted);
        return value;
    }

    @Test

    void sameControllerNewDocumentAndExactU64ReceiptProveReceivedSNotEcho() throws Exception {
        var proof = JavaStopAcknowledgement.stopped(binding("17", "9007199254740993"), binding("18",
                "9007199254740994"), AUTHORITY, PUBLISHER);
        assertEquals("18", proof.stoppedDocument());
        assertEquals("9007199254740994", proof.stoppedReceipt());
    }

    @Test

    void missingAckEchoedOrOldDocumentAndUnchangedReceiptCannotProveStop() {
        var before = binding("17", "10");
        var absent = binding("18", "11");
        absent.remove("acceptedStatus");
        assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(before, absent,
                AUTHORITY, PUBLISHER));
        for (var after : new JsonObject[] {
            binding("17", "11"), binding("16", "11"), binding("18", "10")
        })
            assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(before, after,
                    AUTHORITY, PUBLISHER));
        var echo = binding("18", "11");
        echo.getAsJsonObject("acceptedStatus").addProperty("documentGeneration", "17");
        assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(before, echo,
                AUTHORITY, PUBLISHER));
    }

    @Test

    void sourceReplacementOrForeignReceiptFailsClosed() {
        for (String key : new String[] {
            "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256"
        }) {
            var after = binding("18", "11");
            after.addProperty(key, "foreign");
            assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(binding("17",
                    "10"), after, AUTHORITY, PUBLISHER));
        }
        for (String key : new String[] {
            "daemonPid", "daemonStarttime", "bootId"
        }) {
            var after = binding("18", "11");
            after.getAsJsonObject("acceptedStatus").getAsJsonObject("receipt").addProperty(key,
                    "foreign");
            assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(binding("17",
                    "10"), after, AUTHORITY, PUBLISHER));
        }
    }

    @Test

    void numericCoercionOverflowAndUnknownFieldsAreRejected() {
        var numeric = binding("18", "11");
        numeric.getAsJsonObject("acceptedStatus").getAsJsonObject("receipt").addProperty("sequence",
                11);
        assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(binding("17", "10"),
                numeric, AUTHORITY, PUBLISHER));
        var overflow = binding("18", "18446744073709551616");
        assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(binding("17", "10"),
                overflow, AUTHORITY, PUBLISHER));
        var extra = binding("18", "11");
        extra.addProperty("cleanup", true);
        assertThrows(IOException.class, () -> JavaStopAcknowledgement.stopped(binding("17", "10"),
                extra, AUTHORITY, PUBLISHER));
    }
}
