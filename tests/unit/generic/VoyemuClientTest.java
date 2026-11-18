package ru.voboost.voyemu;
import com.google.gson.*;
import java.time.Duration;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class VoyemuClientTest {
    @Test void sendsTheCurrentVoyemuProtocol() {
        VoyemuClient client = new VoyemuClient((request, timeout) -> { throw new AssertionError("no process expected"); });
        org.junit.jupiter.api.Assertions.assertEquals(1,
            JsonParser.parseString(client.info("probe").json()).getAsJsonObject().get("schema").getAsInt());
    }

    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static void rejects(Runnable r) { try { r.run(); throw new AssertionError(); } catch (IllegalArgumentException | IllegalStateException expected) {} }
    
    @Test
    void clusterRegionRequestsExactFreshPixels(@TempDir Path work) {
        var client = new VoyemuClient((json, timeout) -> { throw new AssertionError(); });
        var session = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
        var region = new VoyemuClient.ClusterRegion(841, 102, 46, 46);
        JsonObject request = JsonParser.parseString(client.captureCluster(
                "roi", "profile", session, work.toAbsolutePath(), 17, region).json()).getAsJsonObject();
        check(request.get("after_sequence").getAsLong() == 17);
        check(request.getAsJsonArray("screens").size() == 1);
        check(request.getAsJsonArray("screens").get(0).getAsString().equals("cluster"));
        JsonObject rect = request.getAsJsonObject("rect");
        check(rect.keySet().equals(java.util.Set.of("x", "y", "width", "height")));
        check(rect.get("x").getAsInt() == 841 && rect.get("y").getAsInt() == 102);
        check(rect.get("width").getAsInt() == 46 && rect.get("height").getAsInt() == 46);
        rejects(() -> new VoyemuClient.ClusterRegion(-1, 0, 46, 46));
        rejects(() -> new VoyemuClient.ClusterRegion(1900, 0, 46, 46));
        rejects(() -> new VoyemuClient.ClusterRegion(0, 700, 46, 46));
        rejects(() -> new VoyemuClient.ClusterRegion(0, 0, 0, 46));
        rejects(() -> client.captureCluster("roi", "profile", session, work.toAbsolutePath(), 0, region));
    }

    @Test
    void acceptsOnlyCorrelatedAndroidReadinessTimeoutAsAResponse() throws Exception {
        var session = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
        String response = """
                {"schema":1,"request_id":"ready","operation_id":"%s","ok":true,"elapsed_ms":60001,
                "result":{"predicate":"android.ready","ready":false,"timed_out":true,
                "last_observation":{"session_id":"%s"}}}
                """.formatted("c".repeat(32), session.id());
        var client = new VoyemuClient((json, timeout) -> new VoyemuClient.ProcessResult(4, false, response, ""));
        var deadline = Duration.ofSeconds(1);
        check(client.execute(client.androidReady("ready", "profile", session, 60000), deadline).state()
                == VoyemuClient.State.RESPONSE);
        check(client.execute(client.info("ready"), deadline).state() == VoyemuClient.State.UNKNOWN);
        for (String invalid : new String[] {
                response.replace("\"ready\":false", "\"ready\":true"),
                response.replace("\"timed_out\":true", "\"timed_out\":false"),
                response.replace(session.id(), "d".repeat(32)) }) {
            var rejected = new VoyemuClient((json, timeout) -> new VoyemuClient.ProcessResult(4, false, invalid, ""));
            check(rejected.execute(rejected.androidReady("ready", "profile", session, 60000), deadline).state()
                    == VoyemuClient.State.UNKNOWN);
        }
    }

    @Test
    void hostContract(@TempDir Path work) throws Exception {
        AtomicInteger count=new AtomicInteger();
        VoyemuClient c=new VoyemuClient((json,t) -> { count.incrementAndGet(); return new VoyemuClient.ProcessResult(null,true,"",""); });
        var a=c.start("original","profile","prepared",null,true);
        rejects(() -> { try { c.execute(a,Duration.ofSeconds(1)); } catch (InterruptedException e) { throw new AssertionError(e); } });
        rejects(() -> { try { c.execute(a,Duration.ofSeconds(1)); } catch (InterruptedException e) { throw new AssertionError(e); } });
        check(count.get()==0);
        JsonObject q=JsonParser.parseString(c.lookup("lookup",a,null).json()).getAsJsonObject();
        check(q.getAsJsonObject("target").get("request_id").getAsString().equals("original") && !q.has("session"));
        var s=new VoyemuClient.Session("a".repeat(32),"b".repeat(32));
        var reboot=c.reboot("reboot","profile",s,true,180000);
        var stop=c.stop("stop","profile",s);
        for (var mutation : new VoyemuClient.Attempt[] {reboot,stop}) rejects(() -> { try { c.execute(mutation,Duration.ofSeconds(1)); } catch (InterruptedException e) { throw new AssertionError(e); } });
        check(count.get()==0);
        q=JsonParser.parseString(c.lookup("lookup2",reboot,null).json()).getAsJsonObject();
        check(q.getAsJsonObject("session").get("id").getAsString().equals(s.id()));
        rejects(() -> c.stop("stop","profile",VoyemuClient.Session.attached("a".repeat(32))));
        rejects(() -> c.start("id","profile","m",Path.of("/m"),true));
        VoyemuClient malformed=new VoyemuClient((json,t)->new VoyemuClient.ProcessResult(0,false,"{}",""));
        check(malformed.execute(malformed.info("i"),Duration.ofSeconds(1)).state()==VoyemuClient.State.UNKNOWN);
        VoyemuClient good=new VoyemuClient((json,t)->new VoyemuClient.ProcessResult(0,false,"{\"schema\":1,\"request_id\":\"i\",\"operation_id\":\""+"a".repeat(32)+"\",\"ok\":true,\"elapsed_ms\":0,\"result\":{}}",""));
        check(good.execute(good.info("i"),Duration.ofSeconds(1)).state()==VoyemuClient.State.RESPONSE);
        check(java.util.Arrays.stream(VoyemuClient.Attempt.class.getDeclaredConstructors()).allMatch(x -> java.lang.reflect.Modifier.isPrivate(x.getModifiers())));
        String prefix="{\"schema\":1,\"request_id\":\"i\",\"operation_id\":\""+"a".repeat(32)+"\",\"ok\":true,\"elapsed_ms\":0,\"result\":{}}";
        for (String bad : new String[] {prefix.replace("\"schema\":1", "\"schema\":2"), prefix.replace("\"schema\":1","\"schema\":\"1\""), prefix.replace("\"elapsed_ms\":0","\"elapsed_ms\":\"0\""), prefix.replace("\"ok\":true","\"ok\":\"true\"")}) {
            VoyemuClient invalid=new VoyemuClient((json,t)->new VoyemuClient.ProcessResult(0,false,bad,""));
            check(invalid.execute(invalid.info("i"),Duration.ofSeconds(1)).state()==VoyemuClient.State.UNKNOWN);
        }
        for (String duplicate : new String[] {
                prefix.replace("\"request_id\":\"i\"", "\"request_id\":\"other\",\"request_id\":\"i\""),
                prefix.replace("\"operation_id\":", "\"operation_id\":\"" + "b".repeat(32) + "\",\"operation_id\":"),
                prefix.replace("\"ok\":true", "\"ok\":false,\"ok\":true"),
                prefix.replace("\"result\":{}", "\"result\":{\"nested\":[{\"x\":1,\"x\":2}]}"),
                prefix + " {}" }) {
            VoyemuClient invalid = new VoyemuClient((json,t) -> new VoyemuClient.ProcessResult(0,false,duplicate,""));
            check(invalid.execute(invalid.info("i"),Duration.ofSeconds(1)).state()==VoyemuClient.State.UNKNOWN);
        }
        String error="{\"schema\":1,\"request_id\":\"i\",\"operation_id\":\""+"a".repeat(32)+"\",\"ok\":false,\"elapsed_ms\":0,\"error\":{\"code\":1,\"message\":\"x\",\"retryable\":false,\"outcome\":\"unknown\"}}";
        VoyemuClient invalidError=new VoyemuClient((json,t)->new VoyemuClient.ProcessResult(4,false,error,""));
        check(invalidError.execute(invalidError.info("i"),Duration.ofSeconds(1)).state()==VoyemuClient.State.UNKNOWN);
        AtomicInteger reads=new AtomicInteger();
        VoyemuClient reader=new VoyemuClient((json,t) -> { reads.incrementAndGet(); JsonObject request=JsonParser.parseString(json).getAsJsonObject(); return new VoyemuClient.ProcessResult(0,false,"{\"schema\":1,\"request_id\":\""+request.get("request_id").getAsString()+"\",\"operation_id\":\""+"a".repeat(32)+"\",\"ok\":true,\"elapsed_ms\":0,\"result\":{}}",""); });
        for (var read : new VoyemuClient.Attempt[] {reader.info("i"), reader.profileGet("p","profile"), reader.status("s","profile",s), reader.lookup("l",reboot,null)}) check(reader.execute(read,Duration.ofSeconds(1)).state()==VoyemuClient.State.RESPONSE);
        check(reads.get()==4);
        Path probe = work.resolve("permissions-probe");
        java.nio.file.Files.writeString(probe, "#!/bin/sh\nstat -f %Lp \"$3\" \"$(dirname \"$3\")\" \"$(dirname \"$3\")/stdout\" \"$(dirname \"$3\")/stderr\" 2>/dev/null || stat -c %a \"$3\" \"$(dirname \"$3\")\" \"$(dirname \"$3\")/stdout\" \"$(dirname \"$3\")/stderr\"\n");
        probe.toFile().setExecutable(true);
        var probeConstructor = Class.forName("ru.voboost.voyemu.VoyemuClient$ProcessTransport").getDeclaredConstructor(Path.class, Path.class);
        probeConstructor.setAccessible(true);
        var probeTransport = (VoyemuClient.Transport) probeConstructor.newInstance(probe, work);
        var permissions = probeTransport.execute("{\"op\":\"info\"}", Duration.ofSeconds(3));
        check(permissions.exitCode() == 0 && permissions.stdout().equals("600\n700\n600\n600\n"));
        java.nio.file.Files.delete(probe);
        Path helper=work.resolve("test-sleeper"); java.nio.file.Files.writeString(helper,"#!/bin/sh\nexec /bin/sleep 20\n"); helper.toFile().setExecutable(true); var constructor=Class.forName("ru.voboost.voyemu.VoyemuClient$ProcessTransport").getDeclaredConstructor(Path.class,Path.class); constructor.setAccessible(true); VoyemuClient.Transport process=(VoyemuClient.Transport) constructor.newInstance(helper,work);
        try { process.execute("{\"op\":\"multimedia.reboot\"}",Duration.ofMillis(20)); throw new AssertionError(); } catch (java.io.IOException expected) {}
        for (String invalidRequest : new String[] {
                "{\"op\":\"multimedia.reboot\",\"op\":\"info\"}",
                "{\"op\":\"info\",\"nested\":[{\"x\":1,\"x\":2}]}",
                "{\"op\":\"info\"} {}" }) {
            try { process.execute(invalidRequest,Duration.ofSeconds(1)); throw new AssertionError(); }
            catch (java.io.IOException expected) {}
        }
        check(process.execute("{\"op\":\"info\"}",Duration.ofMillis(20)).timedOut());
        java.util.concurrent.atomic.AtomicBoolean interrupted=new java.util.concurrent.atomic.AtomicBoolean();
        Thread worker=new Thread(() -> { try { process.execute("{\"op\":\"info\"}",Duration.ofSeconds(20)); } catch (InterruptedException e) { interrupted.set(true); } catch (Exception e) { throw new RuntimeException(e); } });
        worker.start(); Thread.sleep(100); worker.interrupt(); worker.join(3000); check(!worker.isAlive() && interrupted.get()); java.nio.file.Files.delete(helper);
    }
}
