package at.livekit.map;

import org.json.JSONObject;


public abstract class RenderBounds 
{
    public static RenderBounds DEFAULT = new AlwaysInRenderBounds(); //new RenderBounds(-512*2, -512*2, 512*2, 512*2);

    protected int _chunkLeft = -32;
    protected int _chunkTop = -32;
    protected int _chunkRight = 32;
    protected int _chunkBottom = 32;

    protected RenderBounds(){}

    public boolean valid(){ return false; }

    public boolean blockInBounds(int x, int z) { return false; }

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

    public JSONObject toJson() {return null;}

    public static RenderBounds fromJson(JSONObject json) throws Exception {
        String shape = json.getString("shape");
        if(shape.equalsIgnoreCase("ALWAYS_IN")) {
            return new AlwaysInRenderBounds();
        } else
        if(shape.equalsIgnoreCase("CIRCLE")) {
            return new CircleRenderBounds(json.getInt("radius"), json.getInt("offset_x"), json.getInt("offset_z"));
        } else if(shape.equalsIgnoreCase("RECT")) {
            return new RectRenderBounds(json.getInt("left"), json.getInt("top"), json.getInt("right"), json.getInt("bottom"));
        }
        throw new Exception("Invalid RenderBounds shape: "+shape);
    }

    /*public enum RenderShape {
        RECT, CIRCLE
    }*/

    public static class RectRenderBounds extends RenderBounds{
        private int left = -512;
        private int top = -512;
        private int right = 512;
        private int bottom = 512;

        public RectRenderBounds(int left, int top, int right, int bottom) {
            _chunkLeft = (int) Math.floor( (double) left / 16.0 );
            _chunkTop = (int) Math.floor( (double) top / 16.0 );
            _chunkRight = (int) Math.ceil( (double) right / 16.0 );
            _chunkBottom = (int) Math.ceil( (double) bottom / 16.0 );
        }

        @Override
        public boolean blockInBounds(int x, int z) {
            if( x >= left && x < right) {
                if(z >= top && z < bottom) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean valid() {
            if(right - left > 0 && bottom - top > 0) return true;
            return false;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("shape", "RECT");
            json.put("left", left);
            json.put("top", top);
            json.put("right", right);
            json.put("bottom", bottom);
            return json;
        }

        @Override
        public String toString() {
            return "RenderBounds[shape=RECT; left(-x)="+left+"; top(-z)="+top+"; right(x)="+right+"; bottom(z)="+bottom+";]";
        }
    }

    public static class CircleRenderBounds extends RenderBounds {
        private int radius = 0;
        private double radius_sq = 0;
        private int offset_x = 0;
        private int offset_z = 0;

        public CircleRenderBounds(int radius) {
            this(radius, 0, 0);
        }

        public CircleRenderBounds(int radius, int x, int z) {
            this.radius = Math.abs(radius);
            this.radius_sq = Math.pow(this.radius, 2);
            this.offset_x = x;
            this.offset_z = z;

            _chunkLeft = (int) Math.floor((double)((-radius) + offset_x) / 16.0);
            _chunkTop = (int) Math.floor((double)((-radius) + offset_z) / 16.0);
            _chunkRight = (int) Math.ceil((double)(radius + offset_x) / 16.0);
            _chunkBottom = (int) Math.ceil((double)(radius + offset_z) / 16.0);
        }

        @Override
        public boolean blockInBounds(int x, int z) {
            return Math.pow(x - offset_x, 2) + Math.pow(z - offset_z, 2) < radius_sq;
        }

        @Override
        public boolean valid() {
            return radius > 0;
        }

        public int getRadius() {
            return radius;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("shape", "CIRCLE");
            json.put("radius", radius);
            json.put("offset_x", offset_x);
            json.put("offset_z", offset_z);
            return json;
        }

        @Override
        public String toString() {
            return "RenderBounds[shape=CIRCLE; radius="+radius+";]";
        }
    }

    public static class AlwaysInRenderBounds extends RenderBounds {

        @Override
        public boolean chunkInBounds(int x, int z) {
            return true;
        }

        @Override
        public boolean regionInBounds(int x, int z) {
            return true;
        }

        @Override
        public boolean blockInBounds(int x, int z) {
            return true;
        }

        @Override
        public boolean valid() {
            return true;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("shape", "ALWAYS_IN");
            return json;
        }

        @Override
        public String toString() {
            return "RenderBounds[shape=ALWAYS_IN;]";
        }
    }
}
