package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.logging.InstallerLogs;

/** Synthetic protocol fixtures exercise admission only, not OEM runtime. */
class StockBindProvenanceTest {
    static final String BOOT="11111111-1111-1111-1111-111111111111", GENERATION="a".repeat(64), CATALOG="b".repeat(64);
    static final StockBindProvenance.Authority AUTH=new StockBindProvenance.Authority(BOOT,GENERATION,CATALOG,"41","514","6");
    static JsonObject object(Object... pairs) { JsonObject o=new JsonObject(); for(int i=0;i<pairs.length;i+=2) { String k=(String)pairs[i]; Object v=pairs[i+1]; if(v instanceof String s)o.addProperty(k,s);else if(v instanceof Boolean b)o.addProperty(k,b);else o.addProperty(k,(Number)v); } return o; }
    static final class Fixture {
        final List<InstallerLogs.Diagnostic> rows=new ArrayList<>(); final List<InstallerLogs.DaemonEvent> events=new ArrayList<>(); int sequence=1;
        final JsonObject source=object("process","com.qinggan.app.launcher","bootId",BOOT,"targetPid","41","targetStarttime","514","endpointEpoch","6");
        void add(String event,String outcome,JsonObject fields) { rows.add(new InstallerLogs.Diagnostic(BigInteger.valueOf(++sequence),"launcher-apps",event,outcome,fields,source.deepCopy())); }
        Fixture() {
            events.add(new InstallerLogs.DaemonEvent(BigInteger.ONE,"runtime","desired-snapshot-queued",object("process","com.qinggan.app.launcher","generationId",GENERATION,"catalogSha256",CATALOG,"targetPid","41","targetStarttime","514")));
            for(int screen=0;screen<2;screen++) {
                String inv="inventory"+screen, bean="bean"+screen, token="token"+screen, bind="bind"+screen;
                JsonObject identity=object("code_generation",GENERATION,"target_pid",41,"process","com.qinggan.app.launcher","launcher_generation",1,"screen_id",screen,"contract","qinggan-base-v1");
                JsonObject item=object("inventory_id",inv,"index",0,"bean_id",bean,"token",token,"package_name","","icon_resource",123,"icon_resource_name","oem:drawable/icon");add("stock.bind.inventory.item","observed",item);
                JsonObject inventory=identity.deepCopy();inventory.addProperty("inventory_id",inv);inventory.addProperty("count",1);add("stock.bind.inventory","observed",inventory);
                JsonObject id=identity.deepCopy();id.addProperty("inventory_id",inv);id.addProperty("bind_id",bind);add("stock.bind.identity","observed",id);
                JsonObject b=object("bind_id",bind,"bean_id",bean,"token",token,"package_name","","icon_resource",123,"icon_resource_name","oem:drawable/icon","registration","oem-unregistered");
                JsonObject t=object("bind_id",bind,"item_view","item","icon_view","icon","name_view","name","item_tag","old","icon_tag","null","drawable_id","old","drawable_class","Drawable","geometry","bounds","state","visible","label","label","drawable_state","state");
                add("stock.bind.bean","before",b.deepCopy());add("stock.bind.target","before",t.deepCopy());add("stock.bind.bean","boundary",b.deepCopy());add("stock.bind.target","boundary",t.deepCopy());
                add("stock.bind.theme","returned",object("bind_id",bind,"icon_resource",123,"drawable_id","native","drawable_class","Drawable"));add("stock.bind.bean","after",b.deepCopy());t.addProperty("item_tag",bean);t.addProperty("drawable_id","native");add("stock.bind.target","after",t);
                add("stock.bind.complete","completed",object("bind_id",bind,"boundary_unchanged",true,"token_unchanged",true,"resource_unchanged",true,"original_completed",true,"tag_matches_bean",true,"theme_matches_background",true,"managed_drawable",false));
            }
        }
        InstallerLogs.Snapshot before(){return snapshot(0,List.of(),List.of());}
        InstallerLogs.Snapshot current(){return snapshot(sequence,rows,events);}
        InstallerLogs.Snapshot snapshot(int n,List<InstallerLogs.Diagnostic> d,List<InstallerLogs.DaemonEvent> e){var ids=new HashSet<BigInteger>();for(int i=1;i<=n;i++)ids.add(BigInteger.valueOf(i));return new InstallerLogs.Snapshot(BOOT,"writer",BigInteger.valueOf(n),BigInteger.valueOf(n),ids,Instant.EPOCH,d,List.of(),null,e);}
        InstallerLogs.Diagnostic find(String event,String outcome){return rows.stream().filter(r->r.event().equals(event)&&r.outcome().equals(outcome)).findFirst().orElseThrow();}
        List<String> read()throws Exception{return StockBindProvenance.read(before(),current(),AUTH);}
    }
    @Test void completeBothScreensAndNativeBindingsPass()throws Exception { assertEquals(List.of("0:token0","1:token1"),new Fixture().read()); }
    @Test void missingOrSubstitutedEvidenceFails() {
        for(String change:List.of("inventory","theme","completion","resource","boundary","tag","drawable","wrapper","source","registration","boot")) {
            Fixture f=new Fixture(); StockBindProvenance.Authority authority=AUTH;
            switch(change) {
                case "inventory" -> f.rows.remove(f.find("stock.bind.inventory.item","observed"));
                case "theme" -> f.rows.remove(f.find("stock.bind.theme","returned"));
                case "completion" -> f.rows.remove(f.find("stock.bind.complete","completed"));
                case "resource" -> f.find("stock.bind.bean","after").fields().addProperty("icon_resource",124);
                case "boundary" -> f.find("stock.bind.target","boundary").fields().addProperty("geometry","changed");
                case "tag" -> f.find("stock.bind.target","after").fields().addProperty("item_tag","foreign");
                case "drawable" -> f.find("stock.bind.theme","returned").fields().addProperty("drawable_id","foreign");
                case "wrapper" -> f.find("stock.bind.complete","completed").fields().addProperty("managed_drawable",true);
                case "source" -> f.find("stock.bind.identity","observed").sourceFields().addProperty("targetPid","42");
                case "registration" -> f.events.clear();
                case "boot" -> authority=new StockBindProvenance.Authority("22222222-2222-2222-2222-222222222222",GENERATION,CATALOG,"41","514","6");
            }
            var a=authority; assertThrows(Exception.class,()->StockBindProvenance.read(f.before(),f.current(),a),change);
        }
    }
}
