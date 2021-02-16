package at.livekit.map;

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
        if(x > right) {
            x = left;
            z++;
        } 
        if(z > bottom) return null;
        return new Offset(x++, z, mode ==  RenderJobMode.MISSING ? true : false);
    }

    @Override
    public String toString() {
        return "RenderJob[mode="+mode+"; left(-x)="+left+"; top(-z)="+top+"; right(x)="+right+"; bottom(z)="+bottom+"; x="+x+"; z="+z+"]";
    }

    public enum RenderJobMode {
        MISSING, FORCED
    }
}
