package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes the actual read-only script with closed host proc/stat command observations, never a device. */
class ProcessProducerTest {
    @TempDir Path directory;
    private String observe(String overrides)throws Exception {
        String prefix="""
            test() { case "$1" in -f|-d) case "$2" in /data/voboost*) return 0;; esac;; esac; command test "$@"; }
            id() { printf '0\n'; }
            getenforce() { printf 'Enforcing\n'; }
            getprop() { printf '1\n'; }
            readlink() { printf '/data/voboost/voboost-daemon\n'; }
            stat() { if command test "$1" = -L; then printf '8:9:0:0:700:1\n'; return; fi; case "$2" in '%u:%g') printf '0:0\n0:0\n';; '%u:%g:%a:%h') printf '0:0:600:1\n';; *) return 1;; esac; }
            pidof() { case "$1" in com.qinggan.systemservice) printf 303;; com.qinggan.app.setting) printf 404;; com.qinggan.app.launcher) printf 505;; *) return 1;; esac; }
            fixture_head() {
                case "$*" in
                    '-c 37 /proc/sys/kernel/random/boot_id') printf '11111111-1111-1111-1111-111111111111\n';;
                    '-c 17 /data/voboost/run/inject.pid') printf 202;;
                    '-c 17 /data/voboost/run/daemon.pid') printf 201;;
                    '-c 4097 /proc/'*'/stat')
                        p=${3#/proc/};p=${p%/stat}; printf '%s (observed process) S' "$p"; n=0;while command test "$n" -lt 18;do printf ' 0';n=$((n+1));done;printf ' 777\n';;
                    '-c 4097 /proc/202/cmdline') printf '/data/voboost/voboost-inject\000';;
                    '-c 4097 /proc/201/cmdline') printf '/data/voboost/voboost-daemon\000';;
                    '-c 4097 /proc/303/cmdline') printf 'com.qinggan.systemservice\000';;
                    '-c 4097 /proc/404/cmdline') printf 'com.qinggan.app.setting\000';;
                    '-c 4097 /proc/505/cmdline') printf 'com.qinggan.app.launcher\000';;
                    *) command head "$@";;
                esac
            }
            """;
        prefix+="head() { fixture_head \"$@\"; }\n";
        String source=Files.readString(Path.of("../voboost-installer/src-tauri/src/commands/inspect-current-process-identities.sh"));
        Path script=directory.resolve(java.util.UUID.randomUUID()+".sh");Files.writeString(script,prefix+overrides+"\n"+source);
        Process process=new ProcessBuilder("/bin/sh",script.toString()).redirectErrorStream(true).start();
        if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("read-only host script fixture deadline");}
        byte[] output=process.getInputStream().readNBytes(16385);assertTrue(output.length<=16384);assertEquals(0,process.exitValue(),new String(output,StandardCharsets.UTF_8));return new String(output,StandardCharsets.UTF_8);
    }
    @Test void actualScriptPublishesSeparateProducerAndOwnerFromTheirFixedPidFiles()throws Exception {
        String body=observe("");assertTrue(body.contains("process=voboost-inject pid=202 starttime=777 argv0=/data/voboost/voboost-inject\n"));
        assertTrue(body.contains("process=voboost-daemon pid=201 starttime=777 argv0=/data/voboost/voboost-daemon\n"));
        StartupObservation.requireSameProcesses(body,body,"11111111-1111-1111-1111-111111111111");
    }
    @Test void ownerUnavailableDoesNotReplaceOrFabricateTheMandatoryProducer()throws Exception {
        String body=observe("stat() { case \"$3\" in */daemon.pid) return 1;; esac; case \"$2\" in '%u:%g') printf '0:0\\n0:0\\n';; *) printf '0:0:600:1\\n';; esac; }");
        assertTrue(body.contains("process=voboost-daemon unavailable=missing-or-invalid"));
        StartupObservation.requireSameProcesses(body,body,"11111111-1111-1111-1111-111111111111");
    }
    @Test void wrongProducerOwnershipOrArgvNeverFallsBackToTheOwnerPid()throws Exception {
        String body=observe("stat() { case \"$2\" in '%u:%g') printf '0:0\\n0:0\\n';; *) printf '0:0:600:2\\n';; esac; }");
        assertTrue(body.contains("process=voboost-inject unavailable=missing-or-invalid"));
        assertThrows(IOException.class,()->StartupObservation.requireSameProcesses(body,body,"11111111-1111-1111-1111-111111111111"));
    }
    @Test void ownerExecutableCannotStandInForProducerArgv()throws Exception {
        String body=observe("head() { case \"$*\" in '-c 4097 /proc/202/cmdline') printf '/data/voboost/voboost-daemon\\000';; *) fixture_head \"$@\";; esac; }");
        assertTrue(body.contains("process=voboost-inject unavailable=missing-or-invalid"));
        assertTrue(body.contains("process=voboost-daemon pid=201"));
        assertThrows(IOException.class,()->StartupObservation.requireSameProcesses(body,body,"11111111-1111-1111-1111-111111111111"));
    }
    @Test void ownerExactBackingRejectsDeletedRedirectHardlinkAndChangedInode()throws Exception {
        for(String override:new String[]{
            "readlink() { printf '/data/voboost/voboost-daemon (deleted)\\n'; }",
            "readlink() { printf '/data/voboost/other\n'; }",
            "stat() { if command test \"$1\" = -L; then printf '8:9:0:0:755:1\n'; else case \"$2\" in '%u:%g') printf '0:0\n0:0\n';; *) printf '0:0:600:1\n';; esac; fi; }",
            "stat() { if command test \"$1\" = -L; then printf '8:9:0:0:700:2\\n'; else case \"$2\" in '%u:%g') printf '0:0\\n0:0\\n';; *) printf '0:0:600:1\\n';; esac; fi; }",
            "stat() { if command test \"$1\" = -L; then case \"$4\" in /proc/*) printf '8:10:0:0:700:1\\n';; *) printf '8:9:0:0:700:1\\n';; esac; else case \"$2\" in '%u:%g') printf '0:0\\n0:0\\n';; *) printf '0:0:600:1\\n';; esac; fi; }"}) {
            String body=observe(override);assertTrue(body.contains("process=voboost-daemon unavailable=missing-or-invalid"));assertFalse(body.contains("owner-executable pid="));assertTrue(body.contains("process=voboost-inject pid=202"));
        }
    }
    @Test void missingExistingRootAccessRemainsUnavailableWithoutProvisioning()throws Exception {
        String body=observe("id() { printf '1001\\n'; }");assertTrue(body.endsWith("current-process-identities-end=unavailable\n"));assertFalse(body.contains("process="));
    }
}
