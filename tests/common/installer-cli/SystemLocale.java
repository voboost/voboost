package ru.voboost.tests.installer.cli;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A bounded same-boot ActivityManager configuration read, not a property or UI guess. */
public record SystemLocale(String boot, String language, String configuration) {
    private static final Pattern TRADITIONAL = Pattern.compile("^(en|zh|ru)(?:-r(?:[A-Z]{2}|[0-9]{3}))?(?:,|-|$)");
    private static final Pattern BCP47 = Pattern.compile("^b\\+(en|zh|ru)(?:\\+[A-Za-z0-9]{2,8})*(?:,|-|$)");
    public void requireOriginal(String originalBoot, String originalLocale) throws IOException {
        if (!boot.equals(originalBoot) || !java.util.Set.of("en", "zh", "ru").contains(originalLocale) || !language.equals(originalLocale))
            throw new IOException("original system locale is not proven restored in the original boot");
    }
    public static SystemLocale read(String body, String expectedBoot) throws IOException {
        if (body.length() > 8448 || !body.endsWith("\n") || body.indexOf('\r') >= 0 || body.indexOf('\0') >= 0)
            throw new IOException("bounded Android configuration frame required");
        String[] lines = body.split("\n", -1);
        if (lines.length < 4 || !lines[0].equals("locale-observation-v1") || !lines[1].equals("bootId=" + expectedBoot)
                || !expectedBoot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("Android configuration boot frame differs");
        String configuration = null;
        for (int index = 2; index < lines.length; ++index) {
            if (lines[index].startsWith("bootId=")) throw new IOException("ambiguous configuration boot");
            if (lines[index].startsWith("config: ")) {
                if (configuration != null) throw new IOException("ambiguous Android configuration");
                configuration = lines[index].substring(8);
            }
        }
        if (configuration == null || configuration.isBlank() || configuration.length() > 8192)
            throw new IOException("Android configuration unavailable");
        String qualified = configuration.replaceFirst("^(?:mcc[0-9]{1,5}-)?(?:mnc[0-9]{1,5}-)?", "");
        Matcher locale = TRADITIONAL.matcher(qualified);
        if (!locale.find()) { locale = BCP47.matcher(qualified); if (!locale.find()) throw new IOException("primary Android locale is not proven EN/ZH/RU"); }
        return new SystemLocale(expectedBoot, locale.group(1), configuration);
    }
}
