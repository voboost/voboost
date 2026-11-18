package ru.voboost.tests;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Explicit agent host contracts. A process-name resemblance is not an ownership contract. */
public final class TargetOwnerContract {
    public enum Decision { REQUIRED, OWNER_ABSENT, UNKNOWN }
    public record Owner(String packageName, String source) {}
    private static final String ISLAND = "com.qinggan.app.islandapp";
    private static final String STEERING = "com.qinggan.keymanager.service";
    private static final Map<AcceptancePlan.Key, Owner> OWNERS = Map.of(
        new AcceptancePlan.Key("agent/media-popup-external-apps", "android:" + ISLAND),
        new Owner(ISLAND, "voboost-agents/src/main/java/ru/voboost/agents/media/popupexternalapps/MediaPopupExternalAppsAgent.java"),
        new AcceptancePlan.Key("agent/steering-media-app-switcher", "android:" + STEERING),
        new Owner(STEERING, "voboost-agents/src/main/java/ru/voboost/agents/steering/SteeringKeyManagerProfile.java"),
        new AcceptancePlan.Key("agent/steering-media-session", "android:" + STEERING),
        new Owner(STEERING, "voboost-agents/src/main/java/ru/voboost/agents/steering/SteeringKeyManagerProfile.java"),
        new AcceptancePlan.Key("agent/media-apps", "android:com.qinggan.app.launcher"),
        new Owner("com.qinggan.app.launcher", "voboost-agents/src/main/java/ru/voboost/agents/media/apps/MediaAppsSourceRuntime.java"),
        new AcceptancePlan.Key("agent/media-apps", "android:com.qinggan.media"),
        new Owner("com.qinggan.media", "voboost-agents/src/main/java/ru/voboost/agents/media/apps/MediaAppsWindowRuntime.java"),
        new AcceptancePlan.Key("agent/login-activation-skip", "android:com.qinggan.app.login"),
        new Owner("com.qinggan.app.login", "voboost-agents/src/main/java/ru/voboost/agents/login/activationskip/LoginActivationSkipProfilePolicy.java"),
        new AcceptancePlan.Key("agent/vehicle-drive-outing", "android:com.qinggan.app.vehicle"),
        new Owner("com.qinggan.app.vehicle", "voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/outing/VehicleDriveOutingPolicyProfile.java"),
        new AcceptancePlan.Key("agent/vehicle-drive-outing", "android:com.qinggan.app.vehiclesetting"),
        new Owner("com.qinggan.app.vehiclesetting", "voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/outing/VehicleDriveOutingPolicySettingsProfile.java"));
    private static final String I18N_SOURCE = "voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAdmission.java";
    private static final Set<String> I18N_OWNERS = Set.of(
        "com.qinggan.app.setting", "com.qinggan.app.launcher", "com.qinggan.app.vehiclesetting",
        "com.qinggan.app.settingnewwork", "com.qinggan.systemui", "com.qinggan.bluetoothphone", "com.qinggan.media");
    private TargetOwnerContract() {}

    public static java.util.List<AcceptancePlan.Key> keys() {
        java.util.List<AcceptancePlan.Key> keys = new java.util.ArrayList<>(OWNERS.keySet());
        for (String owner : I18N_OWNERS) keys.add(new AcceptancePlan.Key("agent/i18n-ru", "android:" + owner));
        keys.sort(java.util.Comparator.comparing(AcceptancePlan.Key::id).thenComparing(AcceptancePlan.Key::target));
        return java.util.List.copyOf(keys);
    }

    public static Owner owner(AcceptancePlan.Key key) {
        if (key == null) return null;
        Owner owner = OWNERS.get(key);
        if (owner != null || !"agent/i18n-ru".equals(key.id()) || !key.target().startsWith("android:")) return owner;
        String packageName = key.target().substring("android:".length());
        return I18N_OWNERS.contains(packageName) ? new Owner(packageName, I18N_SOURCE) : null;
    }

    /** Re-reads both retained sources; caller must separately authenticate run/session ownership. */
    public static Decision evaluate(AcceptancePlan.Key key, TargetInventory.CrossCheck prior,
            String expectedRun, String expectedSession, String expectedSerial, String expectedBoot) throws IOException {
        Owner owner = owner(key);
        if (owner == null || prior == null) return Decision.UNKNOWN;
        TargetInventory.Capture original = prior.inventory();
        if (!original.runId().equals(expectedRun) || !original.sessionId().equals(expectedSession)
                || !original.serial().equals(expectedSerial) || !original.snapshot().bootId().equals(expectedBoot))
            throw new IOException("inventory scope differs");
        TargetInventory.Capture current = TargetInventory.capture(expectedRun, expectedSession, expectedSerial,
            expectedBoot, original.source());
        if (!current.snapshot().equals(original.snapshot())) throw new IOException("inventory source changed");
        TargetInventory.CrossCheck checked = TargetInventory.crossCheck(current, prior.source());
        if (TargetInventory.applicability(checked, key) == TargetInventory.Applicability.REQUIRED)
            return Decision.REQUIRED;
        for (TargetInventory.Package item : current.snapshot().packages())
            if (owner.packageName().equals(item.name())) return Decision.REQUIRED;
        return Decision.OWNER_ABSENT;
    }
}
