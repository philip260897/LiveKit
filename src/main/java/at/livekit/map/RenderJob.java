package at.livekit.map;

import org.json.JSONObject;

import at.livekit.modules.LiveMapModule.Offset;

public class RenderJob {
    
    private int left;
    private int top;
    private int right;
    private int bottom;

    private int x;
    private int z;

    private RenderJobMode mode = RenderJobMode.MISSING;

    public static RenderJob fromBounds(RenderBounds bounds) {
        return RenderJob.fromBounds(bounds, RenderJobMode.MISSING);
    }

    public static RenderJob fromBounds(RenderBounds bounds, RenderJobMode mode) {
        RenderJob job = new RenderJob();
        job.mode = mode;
        job.left = bounds.getChunkLeft();
        job.top = bounds.getChunkTop();
        job.right = bounds.getChunkRight();
        job.bottom = bounds.getChunkBottom();
        job.x = job.left;
        job.z = job.top;
        return job;
    }

    public Offset next() {
        if(x >= right) {
            x = left;
            z++;
        } 
        if(z >= bottom) return null;
        return new Offset(x++, z, mode ==  RenderJobMode.MISSING ? true : false);
    }

    public float progressPercent() {
        return Math.round(  ( (double)currentCount() / (double)maxCount() * 100f * 100f) ) / 100f;
    }

    public long currentCount() {
        return ((long)(z - top) * (long)(right - left) + (long)(x - left));
    }

    public long maxCount() {
        return ((long)(right-left) * (long)(bottom - top));
    }

    @Override
    public String toString() {
        return "RenderJob[mode="+mode+"; left(-x)="+left+"; top(-z)="+top+"; right(x)="+right+"; bottom(z)="+bottom+"; x="+x+"; z="+z+"]";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("left", left);
        json.put("top", top);
        json.put("right", right);
        json.put("bottom", bottom);
        json.put("x", x);
        json.put("z", z);
        json.put("mode", mode.name());
        return json;
    }

    public static RenderJob fromJson(JSONObject json ) {
        RenderJob job = new RenderJob();
        job.left = json.getInt("left");
        job.right = json.getInt("right");
        job.top = json.getInt("top");
        job.bottom = json.getInt("bottom");
        job.x = json.getInt("x");
        job.z = json.getInt("z");
        job.mode = RenderJobMode.valueOf(json.getString("mode"));
        return job;
    }

    public enum RenderJobMode {
        MISSING, FORCED;
    }
}
