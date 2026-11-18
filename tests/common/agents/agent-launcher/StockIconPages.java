package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.voboost.voyemu.PixelImage;

/** Complete primary page coverage from current native page observations and paired raster captures. */
public final class StockIconPages {
    public record Page(LauncherPage nativePage,LauncherGrid grid,JsonObject tree,PixelImage image) {}
    private static IOException bad(String reason){return new IOException("stock icon pages unavailable: "+reason);}
    private static void complete(List<Page> pages)throws IOException {
        if(pages==null||pages.isEmpty()||pages.size()!=pages.get(0).nativePage().pages())throw bad("complete page set required");
        int count=pages.get(0).nativePage().count();Set<String> labels=new HashSet<>();
        for(int i=0;i<pages.size();i++) {
            Page page=pages.get(i);LauncherPage current=page.nativePage();
            if(current.first()!=i*12||current.count()!=count||current.pages()!=pages.size()||page.grid().items().size()!=current.items().size())throw bad("page order, count or grid differs");
            if(i>0)current.requireSameLifetime(pages.get(i-1).nativePage());
            for(int n=0;n<current.items().size();n++) {
                var nativeItem=current.items().get(n);var gridItem=page.grid().items().get(n);
                if(nativeItem.position()!=gridItem.position()||!nativeItem.label().equals(gridItem.label())||!labels.add(nativeItem.label()))throw bad("missing, duplicated or substituted item");
            }
        }
        if(labels.size()!=count)throw bad("incomplete inventory");
    }
    public static void compare(List<Page> before,List<Page> after)throws IOException {
        complete(before);complete(after);
        if(before.size()!=after.size()||before.get(0).nativePage().count()!=after.get(0).nativePage().count())throw bad("stock inventory changed");
        for(int i=0;i<before.size();i++){Page a=before.get(i),b=after.get(i);StockIconPixels.compare(a.tree(),a.grid(),a.image(),b.tree(),b.grid(),b.image());}
    }
    private StockIconPages() {}
}
