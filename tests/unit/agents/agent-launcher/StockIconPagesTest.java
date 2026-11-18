package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.voyemu.PixelImage;

/** Synthetic native page receipts and PNGs; no runtime effect is claimed. */
class StockIconPagesTest {
    @TempDir Path directory;
    JsonObject rect(int x,int y,int w,int h){JsonObject r=new JsonObject();r.addProperty("x",x);r.addProperty("y",y);r.addProperty("width",w);r.addProperty("height",h);return r;}
    JsonObject node(String id,String parent,String resource,JsonObject bounds){JsonObject n=new JsonObject();n.addProperty("id",id);n.addProperty("parent",parent);n.addProperty("package",LauncherPage.OWNER);n.addProperty("window_id","w");n.addProperty("resource_id",LauncherPage.OWNER+":id/"+resource);n.addProperty("class","android.widget.ImageView");n.addProperty("visible",true);n.addProperty("present",true);n.add("bounds",bounds);n.add("clipped_bounds",bounds.deepCopy());return n;}
    StockIconPages.Page page(String file,boolean changed)throws Exception {
        JsonObject receipt=AgentLauncherPageTest.receipt();receipt.getAsJsonObject("observation").getAsJsonArray("items").get(1).getAsJsonObject().addProperty("label","Second");LauncherPage nativePage=LauncherPage.read(receipt.toString(),"a".repeat(64),AgentLauncherPageTest.identity());
        JsonArray nodes=new JsonArray();List<LauncherGrid.Item> items=new ArrayList<>();
        for(int i=0;i<2;i++){JsonObject root=node("item"+i,"grid","all_app_item",rect(10+i*100,10,60,60));nodes.add(root);nodes.add(node("icon"+i,"item"+i,"item_icon",rect(20+i*100,20,30,30)));items.add(new LauncherGrid.Item(i,i==0?"Stock":"Second",root));}
        JsonObject surface=new JsonObject();surface.addProperty("screen","primary");surface.addProperty("status","ok");surface.addProperty("complete",true);JsonArray windows=new JsonArray();JsonObject window=new JsonObject();window.addProperty("id","w");window.addProperty("read_complete",true);windows.add(window);surface.add("windows",windows);surface.add("nodes",nodes);JsonArray screens=new JsonArray();screens.add(surface);JsonObject tree=new JsonObject();tree.add("screens",screens);
        BufferedImage raster=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);raster.setRGB(30,30,changed?0xff:0x123456);raster.setRGB(130,30,0x345678);Path path=directory.resolve(file).toAbsolutePath();ImageIO.write(raster,"png",path.toFile());
        return new StockIconPages.Page(nativePage,new LauncherGrid(new JsonObject(),items),tree,PixelImage.read(path,1920,720));
    }
    @Test void completeVisiblePageWithIdenticalIconsPasses()throws Exception {StockIconPages.compare(List.of(page("a.png",false)),List.of(page("b.png",false)));}
    @Test void missingDuplicatePageOrChangedIconFails()throws Exception {
        var a=page("a.png",false);var b=page("b.png",false);var changed=page("changed.png",true);
        assertThrows(IOException.class,()->StockIconPages.compare(List.of(a),List.of()));
        assertThrows(IOException.class,()->StockIconPages.compare(List.of(a,a),List.of(b,b)));
        assertThrows(IOException.class,()->StockIconPages.compare(List.of(a),List.of(changed)));
    }
}
