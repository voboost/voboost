package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Finite OEM long-press/edge-hover trajectory, derived only from observed rectangles. Effect needs fresh readback. */
final class LauncherDrag {
    private LauncherDrag() {}
    private static void box(List<Integer> value) {
        if (value == null || value.size() != 4 || value.get(0) < 0 || value.get(1) < 0 || value.get(2) > 1920 || value.get(3) > 720
                || value.get(2) <= value.get(0) || value.get(3) <= value.get(1)) throw new IllegalArgumentException("observed Launcher bounds required");
    }
    private static int mid(List<Integer> value, int axis) { return (value.get(axis) + value.get(axis + 2)) / 2; }
    private static void add(JsonArray points, int x, int y, int time) {
        JsonObject value = new JsonObject(); value.addProperty("x",x); value.addProperty("y",y); value.addProperty("at_ms",time); points.add(value);
    }
    static JsonArray points(int page, List<Integer> item, List<Integer> first, List<Integer> grid) {
        if (page < 0 || page > 2) throw new IllegalArgumentException("bounded Launcher page0..2 required");
        for (List<Integer> value : List.of(item, first, grid)) box(value);
        for (List<Integer> value : List.of(item, first)) if (value.get(0) < grid.get(0) || value.get(1) < grid.get(1)
                || value.get(2) > grid.get(2) || value.get(3) > grid.get(3)) throw new IllegalArgumentException("Launcher item outside observed grid");
        int x = mid(item,0), y = mid(item,1), finishX = mid(first,0), finishY = mid(first,1);
        JsonArray points = new JsonArray(); add(points,x,y,0); add(points,x,y,600);
        if (page > 0) {
            int edgeX = grid.get(0) + Math.max(1,(first.get(0) - grid.get(0))/2);
            for (int index=0; index<page; index++) add(points,edgeX,y,650+index*550);
            add(points,finishX,finishY,650+page*550); add(points,finishX,finishY,850+page*550);
        } else {
            for (int index=1; index<=10; index++) add(points,(int)Math.round(x+(finishX-x)*index/10.0),(int)Math.round(y+(finishY-y)*index/10.0),600+30*index);
            add(points,finishX,finishY,1100); add(points,finishX,finishY,1300);
        }
        return points;
    }
    static void validate(JsonObject request) {
        if (!request.keySet().equals(java.util.Set.of("schema","request_id","op","profile","session","screen","scope","intent","expected","points"))
                || !"input.gesture".equals(request.get("op").getAsString()) || !"primary".equals(request.get("screen").getAsString())
                || !"all_attached".equals(request.get("scope").getAsString()) || !"drag".equals(request.get("intent").getAsString())) throw new IllegalArgumentException("closed Launcher drag request required");
        JsonObject expected=request.getAsJsonObject("expected"), target=expected.getAsJsonObject("target"), window=expected.getAsJsonObject("window");
        if (!expected.keySet().equals(java.util.Set.of("boot_id","source_instance","revision","window","target"))
                || !"com.qinggan.app.launcher".equals(target.get("package").getAsString())
                || !"com.qinggan.app.launcher".equals(window.get("package").getAsString())
                || !window.get("type").isJsonPrimitive() || !window.getAsJsonPrimitive("type").isNumber() || !"1".equals(window.get("type").getAsString())
                || !target.get("bounds").equals(target.get("clipped_bounds"))) throw new IllegalArgumentException("Launcher drag owner differs");
        JsonArray points=request.getAsJsonArray("points"); int size=points.size();
        if (size!=5 && size!=6 && size!=14) throw new IllegalArgumentException("finite Launcher drag trace required");
        int last=-1; for (var item:points) {
            JsonObject point=item.getAsJsonObject(); if (!point.keySet().equals(java.util.Set.of("x","y","at_ms"))) throw new IllegalArgumentException("drag point fields");
            for (String key:List.of("x","y","at_ms")) if (!point.get(key).isJsonPrimitive() || !point.getAsJsonPrimitive(key).isNumber() || !point.get(key).getAsString().matches("[0-9]{1,4}")) throw new IllegalArgumentException("drag point integer");
            int x=point.get("x").getAsInt(),y=point.get("y").getAsInt(),time=point.get("at_ms").getAsInt();
            if (x>=1920 || y>=720 || time<=last || time>2000) throw new IllegalArgumentException("drag point bounds"); last=time;
        }
        JsonObject first=points.get(0).getAsJsonObject(), held=points.get(1).getAsJsonObject(), bounds=target.getAsJsonObject("clipped_bounds");
        if (first.get("at_ms").getAsInt()!=0 || held.get("at_ms").getAsInt()!=600 || !first.get("x").equals(held.get("x")) || !first.get("y").equals(held.get("y"))
                || first.get("x").getAsInt()!=bounds.get("x").getAsInt()+bounds.get("width").getAsInt()/2
                || first.get("y").getAsInt()!=bounds.get("y").getAsInt()+bounds.get("height").getAsInt()/2) throw new IllegalArgumentException("drag start/hold is not the observed item center");
        JsonObject end=points.get(size-1).getAsJsonObject(), previous=points.get(size-2).getAsJsonObject();
        if (!end.get("x").equals(previous.get("x")) || !end.get("y").equals(previous.get("y"))) throw new IllegalArgumentException("drag drop not settled");
        if (size==14) {
            for(int index=1;index<=10;index++) {
                JsonObject point=points.get(index+1).getAsJsonObject();
                int x=(int)Math.round(first.get("x").getAsInt()+(end.get("x").getAsInt()-first.get("x").getAsInt())*index/10.0);
                int y=(int)Math.round(first.get("y").getAsInt()+(end.get("y").getAsInt()-first.get("y").getAsInt())*index/10.0);
                if(point.get("x").getAsInt()!=x || point.get("y").getAsInt()!=y || point.get("at_ms").getAsInt()!=600+30*index) throw new IllegalArgumentException("drag interpolation differs");
            }
            if(previous.get("at_ms").getAsInt()!=1100 || end.get("at_ms").getAsInt()!=1300) throw new IllegalArgumentException("drag settle differs");
        } else {
            int page=size-4; JsonObject edge=points.get(2).getAsJsonObject();
            for(int index=0;index<page;index++) { JsonObject point=points.get(2+index).getAsJsonObject(); if(!point.get("x").equals(edge.get("x")) || !point.get("y").equals(first.get("y")) || point.get("at_ms").getAsInt()!=650+550*index) throw new IllegalArgumentException("drag edge differs"); }
            if(previous.get("at_ms").getAsInt()!=650+page*550 || end.get("at_ms").getAsInt()!=850+page*550) throw new IllegalArgumentException("drag edge settle differs");
        }
    }
}
