package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.voboost.tests.installer.cli.FixtureClassification;
import ru.voboost.tests.logging.InstallerLogs;

/** Fresh scanner decisions tied to independent fixed-package platform identity and live Launcher. */
public final class CatalogClassification {

    public record Proof(String generation, List<String> excluded, List<String> qualified) {
        public Proof {
            excluded = List.copyOf(excluded);
            qualified = List.copyOf(qualified);
        }
    }

    private record Identity(int uid, int flags, boolean application) {}

    private static IOException invalid(String reason) {
        return new IOException("catalog classification unavailable: " + reason);
    }

    private static String text(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isString())
            throw invalid(key);
        return value.get(key).getAsString();
    }

    private static int number(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isNumber()
                || !value.get(key).getAsString().matches("0|-?[1-9][0-9]{0,9}"))
            throw invalid(key);
        try {
            return Integer.parseInt(value.get(key).getAsString());
        } catch (NumberFormatException bad) {
            throw invalid(key);
        }
    }

    private static String generation(JsonObject value) throws IOException {
        if (!value.has("generation") || !value.get("generation").isJsonPrimitive()
                || !value.getAsJsonPrimitive("generation").isNumber()
                || !value.get("generation").getAsString().matches("0|[1-9][0-9]{0,18}"))
            throw invalid("generation");
        String result = value.get("generation").getAsString();
        if (new java.math.BigInteger(result).bitLength() > 63)
            throw invalid("generation bound");
        return result;
    }

    private static boolean flag(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isBoolean())
            throw invalid(key);
        return value.get(key).getAsBoolean();
    }

    private static void source(InstallerLogs.Diagnostic row,
            LauncherPage.Identity owner) throws IOException {
        JsonObject source = row.sourceFields();
        for (var pair : Map.of("process", LauncherPage.OWNER, "bootId", owner.boot(), "targetPid",
                owner.pid(), "targetStarttime", owner.starttime(), "endpointEpoch",
                owner.epoch()).entrySet())
            if (!pair.getValue().equals(source.has(pair.getKey()) ? source.get(pair.getKey()).getAsString() : null))
                throw invalid("live source " + pair.getKey());
    }

    public static Proof read(List<InstallerLogs.Diagnostic> fresh, LauncherPage.Identity owner,
            FixtureClassification fixture) throws IOException {
        fixture.requireOwnedUserApplication();
        if (!owner.boot().equals(fixture.boot()) || fresh.size() > 4096)
            throw invalid("boot/bound");
        List<InstallerLogs.Diagnostic> scan = new ArrayList<>();
        InstallerLogs.Diagnostic start = null, complete = null;
        for (var row : fresh) {
            if (!"launcher-apps".equals(row.agent()))
                continue;
            source(row, owner);
            if ("catalog.scan".equals(row.event()) && "started".equals(row.outcome())) {
                start = row;
                complete = null;
                scan.clear();
            } else if (start != null && "catalog.scan".equals(row.event())
                    && "complete".equals(row.outcome())) {
                complete = row;
            } else if (start != null && complete == null && "catalog.candidate".equals(row.event()))
                scan.add(row);
        }
        if (start == null || complete == null
                || complete.sequence().compareTo(start.sequence()) <= 0)
            throw invalid("fresh complete scan missing");
        String generation = generation(start.fields());
        if (!generation(complete.fields()).equals(generation))
            throw invalid("scan generation");
        Map<String, Identity> identities = new LinkedHashMap<>();
        List<String> excluded = new ArrayList<>(), qualified = new ArrayList<>();
        for (var row : scan)
            if ("identity-observed".equals(row.outcome())) {
                String name = text(row.fields(), "package_name");
                int uid = number(row.fields(), "uid"), flags = number(row.fields(), "flags");
                boolean application = flag(row.fields(), "application_uid");
                if (name.isBlank() || uid < 0 || application != (uid % 100000 >= 10000
                        && uid % 100000 <= 19999))
                    throw invalid("platform identity");
                Identity value = new Identity(uid, flags, application),
                        prior = identities.putIfAbsent(name, value);
                if (prior != null && !prior.equals(value))
                    throw invalid("candidate identity changed");
            }
        for (var entry : identities.entrySet()) {
            String name = entry.getKey();
            Identity id = entry.getValue();
            String exclusion = !id.application() ? "skipped-non-application-uid" : (id.flags() & 129) != 0 ? "skipped-system-application" : null;
            boolean accepted = false, skipped = false;
            for (var row : scan) {
                if (!row.fields().has("package_name") || !name.equals(text(row.fields(),
                        "package_name")))
                    continue;
                if ("qualified".equals(row.outcome()) || exclusion != null
                        && exclusion.equals(row.outcome())) {
                    if (number(row.fields(), "uid") != id.uid() || number(row.fields(),
                            "flags") != id.flags())
                        throw invalid("decision identity");
                    if ("qualified".equals(row.outcome()))
                        accepted = true;
                    else
                        skipped = true;
                }
            }
            if (exclusion != null) {
                if (!skipped || accepted)
                    throw invalid("excluded package admitted or decision missing");
                excluded.add(name);
            } else if (accepted)
                qualified.add(name);
        }
        Identity actual = identities.get("ru.voboost.tests.launcherfixture");
        if (actual == null || actual.uid() != fixture.uid()
                || (actual.flags() & 0xffffffffL) != fixture.flags()
                || !qualified.contains("ru.voboost.tests.launcherfixture") || excluded.isEmpty()
                || number(complete.fields(), "candidates") != qualified.size())
            throw invalid("independent fixture or nonvacuous exclusion differs");
        return new Proof(generation, excluded, qualified);
    }

    private CatalogClassification() {}
}
