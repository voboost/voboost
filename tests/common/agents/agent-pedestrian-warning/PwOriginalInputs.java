package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import ru.voboost.tests.RunFiles;

/** Resolve the actual original QNX role, never the Android reference or a machine-name suffix. */
public final class PwOriginalInputs {
    private PwOriginalInputs() {}
    private static void ordinary(Path path)throws IOException {
        for(Path current=path;current!=null;current=current.getParent())if(Files.isSymbolicLink(current))throw new IOException("linked original input rejected");
    }
    private static JsonObject definition(Path directory)throws IOException {
        Path file=directory.resolve("profile-qnx.json");ordinary(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>131072)throw new IOException("ordinary bounded QNX profile required");return RunFiles.read(file);
    }
    public static Path repository(Path executable,String profile)throws IOException {
        Path binary=executable.toRealPath();for(Path parent=binary.getParent();parent!=null;parent=parent.getParent()) {
            if(Files.isRegularFile(parent.resolve("profiles").resolve(profile).resolve("profile-qnx.json")))return parent;
        }
        throw new IOException("current Voyemu executable has no original-input root for the selected QNX profile");
    }
    public static Path system(Path root,String profile)throws IOException {
        if(root==null||!root.isAbsolute()||!root.equals(root.toRealPath())||profile==null||!profile.matches("[a-z0-9][a-z0-9._-]{0,127}"))throw new IOException("canonical original repository and exact profile required");
        Path profiles=root.resolve("profiles"),directory=profiles.resolve(profile);JsonObject qnx=definition(directory);Path local=root.resolve(".local/profiles").resolve(profile).resolve("qnx");ordinary(local);
        if(!Files.exists(local,LinkOption.NOFOLLOW_LINKS)) {
            Path currentFile=directory.resolve("profile.json");ordinary(currentFile);if(!Files.isRegularFile(currentFile,LinkOption.NOFOLLOW_LINKS)||Files.size(currentFile)>1048576)throw new IOException("ordinary bounded current profile required");JsonObject current=RunFiles.read(currentFile);JsonObject capture=current.has("capture")&&current.get("capture").isJsonObject()?current.getAsJsonObject("capture"):new JsonObject();
            String reference=capture.has("referenceProfile")&&capture.get("referenceProfile").isJsonPrimitive()&&capture.getAsJsonPrimitive("referenceProfile").isString()?capture.get("referenceProfile").getAsString():"";
            if(!reference.matches("[a-z0-9][a-z0-9._-]{0,127}")||reference.equals(profile)||!qnx.equals(definition(profiles.resolve(reference))))throw new IOException("explicit identical QNX reference profile required for original fallback");
            local=root.resolve(".local/profiles").resolve(reference).resolve("qnx");ordinary(local);
        }
        try {
            if(!"system_la.img".equals(qnx.getAsJsonObject("originals").getAsJsonObject("system_la.img").get("file").getAsString()))throw new IOException("original system_la role differs");
        }catch(RuntimeException malformed){throw new IOException("original QNX system role missing",malformed);}
        Path source=local.resolve("system_la.img");ordinary(source);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)==0)throw new IOException("original QNX system image unavailable");return source.toRealPath();
    }
}
