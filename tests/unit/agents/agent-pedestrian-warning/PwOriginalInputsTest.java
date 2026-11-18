package ru.voboost.tests.installer.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class PwOriginalInputsTest {
    @TempDir Path root;
    private void profile(String name,String json)throws Exception{Path p=Files.createDirectories(root.resolve("profiles").resolve(name));Files.writeString(p.resolve("profile-qnx.json"),json);Files.writeString(p.resolve("profile.json"),"{\"referenceProfile\":\"wrong-android\",\"capture\":{\"referenceProfile\":\"free-source\"}}");}
    private Path original(String name)throws Exception{Path p=Files.createDirectories(root.resolve(".local/profiles").resolve(name).resolve("qnx")).resolve("system_la.img");Files.writeString(p,"original fixture");return p.toRealPath();}
    private static final String QNX="{\"originals\":{\"system_la.img\":{\"file\":\"system_la.img\"}}}";
    @Test void ownOriginalWinsOverAndroidAndCaptureReferences()throws Exception{profile("free-current",QNX);assertEquals(original("free-current"),PwOriginalInputs.system(root.toRealPath(),"free-current"));}
    @Test void onlyExplicitEqualQnxReferenceCanSupplyMissingOwnInputs()throws Exception{profile("free-current",QNX);profile("free-source",QNX);assertEquals(original("free-source"),PwOriginalInputs.system(root.toRealPath(),"free-current"));Files.writeString(root.resolve("profiles/free-source/profile-qnx.json"),"{\"originals\":{}}");assertThrows(Exception.class,()->PwOriginalInputs.system(root.toRealPath(),"free-current"));}
    @Test void ExistingIncompleteOwnDirectoryNeverBorrowsDifferentOriginal()throws Exception{profile("free-current",QNX);profile("free-source",QNX);original("free-source");Files.createDirectories(root.resolve(".local/profiles/free-current/qnx"));assertThrows(Exception.class,()->PwOriginalInputs.system(root.toRealPath(),"free-current"));}
    @Test void LinkedOriginalAndUnsafeProfileAreRejected()throws Exception{profile("free-current",QNX);Path real=original("free-current");Path target=root.resolve("elsewhere");Files.move(real,target);Files.createSymbolicLink(real,target);assertThrows(Exception.class,()->PwOriginalInputs.system(root.toRealPath(),"free-current"));assertThrows(Exception.class,()->PwOriginalInputs.system(root.toRealPath(),"../free-current"));}
    @Test void originalInputsResolveWithoutAnyImplementationSourceTree()throws Exception {profile("free-current",QNX);original("free-current");Path binary=root.resolve("voyemu");Files.writeString(binary,"host fixture");assertEquals(root.toRealPath(),PwOriginalInputs.repository(binary,"free-current"));assertFalse(Files.exists(root.resolve("src")));}
}
