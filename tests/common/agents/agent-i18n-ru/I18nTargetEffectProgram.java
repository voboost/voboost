package ru.voboost.tests.agents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RunFiles;

/** Replays a current owner resource receipt. Disabled OEM-resource observation is not yet available. */
public final class I18nTargetEffectProgram {
    public record Entry(AcceptancePlan.Key key, AcceptanceReport.Result result,
            I18nResourceReadbacks.TargetReadback enabled) {}

    /** The source is the actual retained official RuntimeObservation, not a caller-supplied pass flag. */
    public static Entry evaluate(AcceptancePlan.Key key, RuntimeObservation.Identity identity, Path source) throws IOException {
        if (!"agent/i18n-ru".equals(key.id()) || !key.target().startsWith("android:"))
            throw new IOException("i18n Android target key required");
        String owner=key.target().substring("android:".length());
        if (!I18nResourceReadbacks.PROFILES.containsKey(owner)) throw new IOException("unknown i18n target");
        if (source==null || !source.isAbsolute() || !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("retained runtime observation required");
        RuntimeObservation runtime=RuntimeObservation.read(RunFiles.read(source),identity);
        var enabled=I18nResourceReadbacks.readCurrent(runtime,"ru").requireTarget(owner);
        return new Entry(key,new AcceptanceReport.Result(GateStatus.BLOCKED,
                "Current Russian owner resources and endpoint observed; independent OEM resource restoration after disable and actual effect after reboot are not yet witnessed",List.of(source)),enabled);
    }
    /** Only a BLOCKED result can be published until concrete disabled/reboot source validators exist. */
    public static Entry record(AcceptancePlan.Key key, RuntimeObservation.Identity identity, Path source) throws IOException {
        Entry entry=evaluate(key,identity,source);
        CaseEvidence.record(entry.key(),entry.result());
        return entry;
    }
    private I18nTargetEffectProgram() {}
}
