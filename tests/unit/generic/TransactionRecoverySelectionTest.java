package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionRecoverySelectionTest {
    @Test void failedRequiredCaseCannotBorrowAnUnrelatedPassingSummary() {
        TransactionRecoverySelection.Suite suite = TransactionRecoverySelection.SUITES.get(3);
        String output = "test " + suite.cases().get(0) + " ... FAILED\n"
                + "test unrelated::test ... ok\n"
                + "test result: ok. 1 passed; 0 failed; 0 ignored; 0 measured; 40 filtered out; finished in 0.01s\n";
        assertThrows(IOException.class, () -> TransactionRecoverySelection.requirePassed(output, suite));
    }
    @Test void exactOwningSelectionRetainsAllSevenRecoveryCases() throws Exception {
        assertEquals(7, TransactionRecoverySelection.SUITES.stream().mapToInt(suite -> suite.cases().size()).sum());
        for (TransactionRecoverySelection.Suite suite : TransactionRecoverySelection.SUITES) {
            List<String> command = TransactionRecoverySelection.command(Path.of("/trusted/cargo"), suite);
            assertEquals(List.of("/trusted/cargo", "test", "--locked", "--offline", "-p", suite.packageName(),
                    "--bin", suite.binary(), suite.filter(), "--", "--test-threads=1"), command);
            TransactionRecoverySelection.requirePassed(passed(suite), suite);
        }
        TransactionRecoverySelection.Suite generation = TransactionRecoverySelection.SUITES.get(3);
        assertEquals("voboost-daemon", generation.packageName());
        assertEquals("voboost-inject", generation.binary());
    }

    @Test void emptyIgnoredDuplicateAndContradictoryTerminalsAreRejected() {
        for (TransactionRecoverySelection.Suite suite : TransactionRecoverySelection.SUITES) {
            String valid = passed(suite);
            for (String invalid : List.of(
                    "test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 100 filtered out;\n",
                    valid.replace(" ... ok", " ... ignored"),
                    valid.replace("0 ignored", "1 ignored"),
                    valid + "test result: FAILED. 0 passed; 1 failed; 0 ignored; 0 measured; 0 filtered out;\n",
                    valid + valid,
                    valid + "test unrelated::test ... ok\n",
                    valid.replace("test result:", "prefix test result:"),
                    valid.replace(" ... ok", " ... FAILED"))) {
                assertThrows(IOException.class, () -> TransactionRecoverySelection.requirePassed(invalid, suite));
            }
        }
    }

    @Test void unknownExecutableCannotBeSelected() {
        TransactionRecoverySelection.Suite generation = TransactionRecoverySelection.SUITES.get(3);
        TransactionRecoverySelection.Suite wrong = new TransactionRecoverySelection.Suite(
                generation.packageName(), "voboost-daemon", generation.filter(), generation.cases());
        assertThrows(IllegalArgumentException.class,
                () -> TransactionRecoverySelection.command(Path.of("/trusted/cargo"), wrong));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionRecoverySelection.requirePassed(passed(generation), wrong));
    }

    private static String passed(TransactionRecoverySelection.Suite suite) {
        return String.join("\n", suite.cases().stream().map(name -> "test " + name + " ... ok").toList())
                + "\ntest result: ok. " + suite.cases().size()
                + " passed; 0 failed; 0 ignored; 0 measured; 100 filtered out; finished in 0.01s\n";
    }

    @Test void retainedSerialOutputAllowsDiagnosticsBeforeTheTerminalOk() throws Exception {
        TransactionRecoverySelection.Suite suite = TransactionRecoverySelection.SUITES.get(0);
        TransactionRecoverySelection.requirePassed(serialOutput(suite), suite);
    }

    @Test void serialDiagnosticsCannotReplaceTheTerminalResult() {
        TransactionRecoverySelection.Suite suite = TransactionRecoverySelection.SUITES.get(0);
        String valid = serialOutput(suite);
        for (String invalid : List.of(
                valid.replace("\nok\n", "\n"),
                valid.replace("\nok\n", "\nFAILED\n"),
                valid.replace("\nok\n", "\nignored\n"),
                valid.replace(diagnostic(), "null"),
                valid.replace(diagnostic(), "true"),
                valid.replace(diagnostic(), "{\"schemaVersion\":1"),
                valid.replace(diagnostic(), "{\"level\":\"info\",\"level\":\"error\"}"),
                valid.replace(diagnostic(), diagnostic() + " trailing"),
                valid.replace(diagnostic(), "unrecognized output"),
                valid.replace("\nok\n", "\n \nok\n"))) {
            assertThrows(IOException.class, () -> TransactionRecoverySelection.requirePassed(invalid, suite));
        }
    }

    // Reduced actual voboost-install libtest stdout; producer identity is intentionally omitted.
    private static String serialOutput(TransactionRecoverySelection.Suite suite) {
        StringBuilder output = new StringBuilder("running 2 tests\n");
        for (String name : suite.cases()) {
            output.append("test ").append(name).append(" ... ").append(diagnostic()).append('\n');
            output.append(diagnostic()).append("\nok\n");
        }
        return output + "\ntest result: ok. 2 passed; 0 failed; 0 ignored; 0 measured; "
                + "141 filtered out; finished in 0.93s\n";
    }

    private static String diagnostic() {
        return "{\"schemaVersion\":1,\"sequence\":1,\"timestamp\":\"2026-10-29T22:43:09.101Z\","
                + "\"source\":{\"kind\":\"daemon\"},\"level\":\"info\","
                + "\"component\":\"install.boot-readiness\",\"message\":\"Waiting for Android startup\",\"fields\":{}}";
    }

}
