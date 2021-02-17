package at.livekit.map;

import org.json.JSONObject;

public class RenderBounds 
{
    public static RenderBounds DEFAULT = new RenderBounds(-512*2, -512*2, 512*2, 512*2);

    private int left = -512;
    private int top = -512;
    private int right = 512;
    private int bottom = 512;

    private int radius = 0;
    private double radius_sq = 0;
    private RenderShape shape;

    private int _chunkLeft = -32;
    private int _chunkTop = -32;
    private int _chunkRight = 32;
    private int _chunkBottom = 32;

    public RenderBounds(int radius) {
        this.radius = Math.abs(radius);
        this.radius_sq = Math.pow(this.radius, 2);
        shape = RenderShape.CIRCLE;

        initialize();
    }

    public RenderBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        shape = RenderShape.RECT;

        initialize();
    }

    private void initialize() {
        if(shape == RenderShape.RECT) {
            _chunkLeft = (int) Math.floor( (double) left / 16.0 );
            _chunkTop = (int) Math.floor( (double) top / 16.0 );
            _chunkRight = (int) Math.ceil( (double) right / 16.0 );
            _chunkBottom = (int) Math.ceil( (double) bottom / 16.0 );
        } else if(shape == RenderShape.CIRCLE) {
            _chunkLeft = (int) Math.floor((double)-radius / 16.0);
            _chunkTop = (int) Math.floor((double)-radius / 16.0);
            _chunkRight = (int) Math.ceil((double)radius / 16.0);
            _chunkBottom = (int) Math.ceil((double)radius / 16.0);
        }
    }

    public boolean valid() {
        if(shape == RenderShape.CIRCLE) {
            return radius > 0;
        }
        if(shape == RenderShape.RECT) {
            if(right - left > 0 && bottom - top > 0) return true;
        }
        return false;
    }

    public boolean regionInBounds(int x, int z) {
        if(x >= Math.floor((double)_chunkLeft/32.0) && x < Math.ceil((double)_chunkRight/32.0)) {
            if(z >= Math.floor((double)_chunkTop/32.0) && z < Math.ceil((double)_chunkBottom/32.0)) {
                return true;
            }
        }
        return false;
    }

    public boolean chunkInBounds(int x, int z) {
        if(x >= _chunkLeft && x < _chunkRight) {
            if( z >= _chunkTop && z < _chunkBottom) {
                return true;
            }
        }
        return false;
    }

    public boolean blockInBounds(int x, int z) {
        if(shape == RenderShape.RECT) {
            if( x >= left && x < right) {
                if(z >= top && z < bottom) {
                    return true;
                }
            }
        }
        if(shape == RenderShape.CIRCLE) {
            return Math.pow(x - 0, 2) + Math.pow(z - 0, 2) < radius_sq;
        }
        return false;
    }

    public int getChunkLeft() {
        return _chunkLeft;
    }

    public int getChunkRight() {
        return _chunkRight;
    }

    public int getChunkTop() {
        return _chunkTop;
    }

    public int getChunkBottom() {
        return _chunkBottom;
    }

    @Override
    public String toString() {
        return shape == RenderShape.RECT ?  "RenderBounds[shape="+shape+"; left(-x)="+left+"; top(-z)="+top+"; right(x)="+right+"; bottom(z)="+bottom+";]" :
                                            "RenderBounds[shape="+shape+"; radius="+radius+";]" ;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("shape", shape.name());
        json.put("left", left);
        json.put("right", right);
        json.put("top", top);
        json.put("bottom", bottom);
        json.put("radius", radius);
        return json;
    }

    public static RenderBounds fromJson(JSONObject json) throws Exception {
        if(json.getString("shape").equalsIgnoreCase("CIRCLE")) {
            return new RenderBounds(json.getInt("radius"));
        } else {
            return new RenderBounds(json.getInt("left"), json.getInt("top"), json.getInt("right"), json.getInt("bottom"));
        }
    }

    public enum RenderShape {
        RECT, CIRCLE
    }
}
