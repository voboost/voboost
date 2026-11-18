package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.voyemu.PixelImage;

/** Synthetic raster/tree tests; real OEM capture is a separate gate. */
class StockIconPixelsTest {
    @TempDir Path directory;
    JsonObject rect(int x,int y,int width,int height){JsonObject r=new JsonObject();r.addProperty("x",x);r.addProperty("y",y);r.addProperty("width",width);r.addProperty("height",height);return r;}
    JsonObject node(String id,String parent,String resource,JsonObject bounds){JsonObject n=new JsonObject();n.addProperty("id",id);n.addProperty("parent",parent);n.addProperty("package",LauncherPage.OWNER);n.addProperty("window_id","w");n.addProperty("resource_id",LauncherPage.OWNER+":id/"+resource);n.addProperty("class","android.widget.ImageView");n.addProperty("visible",true);n.addProperty("present",true);n.add("bounds",bounds);n.add("clipped_bounds",bounds.deepCopy());return n;}
    JsonObject tree(){JsonObject ui=new JsonObject(),s=new JsonObject();s.addProperty("screen","primary");s.addProperty("status","ok");s.addProperty("complete",true);JsonArray windows=new JsonArray();JsonObject window=new JsonObject();window.addProperty("id","w");window.addProperty("read_complete",true);windows.add(window);s.add("windows",windows);JsonArray nodes=new JsonArray();nodes.add(node("item","grid","all_app_item",rect(10,10,60,60)));nodes.add(node("icon","item","item_icon",rect(20,20,30,30)));s.add("nodes",nodes);JsonArray screens=new JsonArray();screens.add(s);ui.add("screens",screens);return ui;}
    LauncherGrid grid(String label){return new LauncherGrid(new JsonObject(),List.of(new LauncherGrid.Item(0,label,node("item","grid","all_app_item",rect(10,10,60,60)))));}
    PixelImage image(String name,boolean changed,boolean blank)throws Exception {BufferedImage i=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);if(!blank)i.setRGB(30,30,changed?0xff00ff:0x123456);Path p=directory.resolve(name).toAbsolutePath();ImageIO.write(i,"png",p.toFile());return PixelImage.read(p,1920,720);}
    @Test void sameIconUnderSameLabelAndBoundsPasses()throws Exception {StockIconPixels.compare(tree(),grid("Cloud Space"),image("a.png",false,false),tree(),grid("Cloud Space"),image("b.png",false,false));}
    @Test void onlyTheAppendedOwnedFixtureIsAllowed()throws Exception {
        PixelImage a=image("a.png",false,false),b=image("b.png",false,false);LauncherGrid base=grid("A");
        LauncherGrid appended=new LauncherGrid(new JsonObject(),List.of(base.items().get(0),new LauncherGrid.Item(1,"Voboost Fixture",node("fixture","grid","all_app_item",rect(80,10,60,60)))));
        StockIconPixels.compare(tree(),base,a,tree(),appended,b);
        LauncherGrid foreign=new LauncherGrid(new JsonObject(),List.of(base.items().get(0),new LauncherGrid.Item(1,"Foreign",node("foreign","grid","all_app_item",rect(80,10,60,60)))));
        assertThrows(IOException.class,()->StockIconPixels.compare(tree(),base,a,tree(),foreign,b));
    }
    @Test void changedPixelsOrUnboundIconNeverPass()throws Exception {
        PixelImage a=image("a.png",false,false),b=image("b.png",false,false),changed=image("changed.png",true,false),blank=image("blank.png",false,true);
        assertThrows(IOException.class,()->StockIconPixels.compare(tree(),grid("A"),a,tree(),grid("A"),changed));
        assertThrows(IOException.class,()->StockIconPixels.compare(tree(),grid("A"),blank,tree(),grid("A"),blank));
        assertThrows(IOException.class,()->StockIconPixels.compare(tree(),grid("A"),a,tree(),grid("B"),b));
        JsonObject moved=tree();moved.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("parent","foreign");
        assertThrows(IOException.class,()->StockIconPixels.compare(tree(),grid("A"),a,moved,grid("A"),b));
        JsonObject clipped=tree();clipped.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("clipped_bounds").addProperty("width",1);
        assertThrows(IOException.class,()->StockIconPixels.compare(tree(),grid("A"),a,clipped,grid("A"),b));
    }
}
