package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PwSignalWaitTest {
    private static PwSignalWait.Observation value(boolean ack, long seq) {
        return new PwSignalWait.Observation("boot", "1", ack, 2, seq);
    }
    private static class Port implements PwSignalWait.Port {
        final List<PwSignalWait.Observation> values;
        int reads;
        long now;
        Port(PwSignalWait.Observation... values) { this.values = List.of(values); }
        public PwSignalWait.Observation read() { return values.get(Math.min(reads++, values.size()-1)); }
        public long now() { return now; }
        public void pause(long nanos) { assertTrue(nanos > 0); now += nanos; }
    }
    @Test void actualNumericAndStringGenerationFormsRemainBounded() throws Exception {
        for (String json : List.of("1", "\"1\"", "18446744073709551615"))
            assertFalse(PwSignalWait.generation(com.google.gson.JsonParser.parseString(json)).isEmpty());
        for (String json : List.of("null", "true", "0", "-1", "1.0", "1e0", "\"01\"", "18446744073709551616", "{}"))
            assertThrows(IOException.class,()->PwSignalWait.generation(com.google.gson.JsonParser.parseString(json)),json);
    }
    @Test void delayedQnxDeliveryUsesOnlyFreshReads() throws Exception {
        Port port = new Port(value(false, 1), value(true, 1), value(true, 2));
        assertEquals(2, PwSignalWait.await(port,"boot","1").acknowledgedSequence());
        assertEquals(3, port.reads);
    }
    @Test void staleQnxAckHasOneBoundedDeadline() {
        Port port = new Port(value(true, 1));
        assertThrows(IOException.class,()->PwSignalWait.await(port,"boot","1"));
        assertEquals(Duration.ofSeconds(5).toNanos(),port.now);
    }
    @Test void epochChangesAndInvalidDataNeverRetry() {
        for (var sample : List.of(
                new PwSignalWait.Observation("foreign","1",true,2,2),
                new PwSignalWait.Observation("boot","2",true,2,2),
                value(true, 3),new PwSignalWait.Observation("boot","1",true,0,0))) {
            Port port = new Port(sample);
            assertThrows(IOException.class,()->PwSignalWait.await(port,"boot","1"));
            assertEquals(1,port.reads);
        }
    }
    @Test void lateQnxAcknowledgementCannotBecomeSuccess() {
        PwSignalWait.Port late = new Port(value(true, 2)) {
            @Override public PwSignalWait.Observation read() { now += Duration.ofSeconds(5).toNanos(); return super.read(); }
        };
        assertThrows(IOException.class,()->PwSignalWait.await(late,"boot","1"));
    }
}
