package ru.voboost.tests.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class VisualReportTest {
    @TempDir Path directory;
    private Path xml() throws IOException { return Files.createDirectory(directory.resolve("xml")); }
    private Path images() throws IOException { return Files.createDirectory(directory.resolve("images")); }
    private Path work() throws IOException { return RunFiles.directory(directory.resolve("evidence")); }
    private Path suite(Path xml, String name, String failures) throws IOException {
        return Files.writeString(xml.resolve("TEST-" + name + ".xml"), "<testsuite name=\"" + name + "\" tests=\"1\" failures=\"" + failures + "\" errors=\"0\" skipped=\"0\"><testcase name=\"renders\">" + (failures.equals("0") ? "" : "<failure>changed image</failure>") + "</testcase></testsuite>");
    }
    @Test void allFailuresAndCurrentImagesAreRetainedBeforeVerdict() throws Exception {
        Path xml = xml(), images = images(), work = work();
        suite(xml, "A", "1"); suite(xml, "B", "1");
        Files.writeString(images.resolve("a_compare.png"), "current comparison");
        Path stale = Files.writeString(images.resolve("old_compare.png"), "stale comparison"); Files.setLastModifiedTime(stale, FileTime.fromMillis(1));
        var result = VisualReport.retain(xml, images, work, Set.of("A", "B"), 1000, 0);
        assertFalse(result.passed()); assertEquals(2, result.tests()); assertEquals(2, result.problems().size());
        assertTrue(Files.isRegularFile(work.resolve("results/TEST-A.xml"))); assertTrue(Files.isRegularFile(work.resolve("results/TEST-B.xml")));
        assertEquals("current comparison", Files.readString(work.resolve("images/a_compare.png")));
        assertFalse(Files.exists(work.resolve("images/old_compare.png")));
        assertTrue(Files.readString(work.resolve("index.html")).contains("images/a_compare.png"));
        assertEquals(2, RunFiles.read(result.report()).getAsJsonArray("suites").size());
    }
    @Test void greenSubsetAndStaleSuiteCannotBecomeCompletePass() throws Exception {
        Path xml = xml(), images = images(), work = work();
        suite(xml, "A", "0"); Path stale = suite(xml, "B", "0"); Files.setLastModifiedTime(stale, FileTime.fromMillis(1));
        var result = VisualReport.retain(xml, images, work, Set.of("A", "B", "C"), 1000, 0);
        assertFalse(result.passed());
        assertTrue(result.problems().contains("stale result: TEST-B.xml"));
        assertTrue(result.problems().contains("missing visual suite: B")); assertTrue(result.problems().contains("missing visual suite: C"));
    }
    @Test void exactFreshSuiteInventoryPassesAndReferenceInputsStayUnchanged() throws Exception {
        Path xml = xml(), images = images(), work = work();
        suite(xml, "A", "0"); suite(xml, "B", "0");
        Path source = Files.createDirectory(directory.resolve("source"));
        Path first = Files.writeString(source.resolve("ATestVisual.java"), "package example;\nclass ATestVisual {}\n");
        Files.writeString(source.resolve("BTestVisual.java"), "package example;\nclass BTestVisual {}\n");
        assertEquals(Set.of("example.ATestVisual", "example.BTestVisual"), VisualReport.sourceSuites(source));
        String original = Files.readString(first);
        var result = VisualReport.retain(xml, images, work, Set.of("A", "B"), 1000, 0);
        assertTrue(result.passed()); assertEquals(2, result.tests()); assertEquals(original, Files.readString(first));
    }
    @Test void duplicateSuiteAndExternalXmlEntitiesFailClosed() throws Exception {
        Path xml = xml(), images = images(), work = work();
        Path original = suite(xml, "A", "0"); Files.copy(original, xml.resolve("TEST-duplicate.xml"));
        Files.writeString(xml.resolve("TEST-external.xml"), "<!DOCTYPE testsuite SYSTEM \"file:///etc/passwd\"><testsuite name=\"B\" tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        var result = VisualReport.retain(xml, images, work, Set.of("A", "B"), 1000, 0);
        assertFalse(result.passed()); assertEquals(3, RunFiles.read(result.report()).getAsJsonArray("suites").size());
        assertTrue(result.problems().contains("missing visual suite: B"));
    }
    @Test void testcaseFailureCannotHideBehindZeroSummaryCounters() throws Exception {
        Path xml = xml(), images = images(), work = work();
        for (String outcome : java.util.List.of("failure", "error", "skipped")) {
            Path file = suite(xml, outcome, "0");
            Files.writeString(file, Files.readString(file).replace("<testcase name=\"renders\"></testcase>", "<testcase name=\"renders\"><" + outcome + ">actual negative result</" + outcome + "></testcase>"));
        }
        var result = VisualReport.retain(xml, images, work, Set.of("failure", "error", "skipped"), 1000, 0);
        assertFalse(result.passed()); assertEquals(3, result.problems().size());
        assertEquals(3, RunFiles.read(result.report()).getAsJsonArray("suites").size());
    }
    @Test void duplicateTestcaseCannotStandInForAnotherRequiredTest() throws Exception {
        Path xml = xml(), images = images(), work = work();
        Path file = suite(xml, "A", "0");
        Files.writeString(file, Files.readString(file).replace("tests=\"1\"", "tests=\"2\"").replace("</testcase>", "</testcase><testcase name=\"renders\"/>"));
        assertFalse(VisualReport.retain(xml, images, work, Set.of("A"), 1000, 0).passed());
    }
    @Test void nonzeroVisualProcessCannotPassEvenWithGreenSuiteFiles() throws Exception {
        Path xml = xml(), images = images(), work = work(); suite(xml, "A", "0");
        var result = VisualReport.retain(xml, images, work, Set.of("A"), 1000, 1);
        assertFalse(result.passed()); assertEquals(1, RunFiles.read(result.report()).get("processExit").getAsInt());
        assertTrue(Files.isRegularFile(work.resolve("results/TEST-A.xml")));
    }
    @Test void symlinkedEvidenceIsNotCopied() throws Exception {
        Path xml = xml(), images = images(), work = work();
        Path foreign = Files.writeString(directory.resolve("foreign.xml"), "not an owned result");
        Files.createSymbolicLink(xml.resolve("TEST-foreign.xml"), foreign);
        assertThrows(IOException.class, () -> VisualReport.retain(xml, images, work, Set.of("A"), 1000, 0));
        assertFalse(Files.exists(work.resolve("results/TEST-foreign.xml")));
    }
}
