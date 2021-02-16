package at.livekit.map;

public class RenderBounds 
{
    private int left = -512;
    private int top = -512;
    private int right = 511;
    private int bottom = 511;

    private int radius = 0;
    private RenderShape shape;

    private int _chunkLeft = -32;
    private int _chunkTop = -32;
    private int _chunkRight = 31;
    private int _chunkBottom = 31;

    public RenderBounds(int radius) {
        this.radius = radius;
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
            _chunkRight = (int) Math.floor( (double) right / 16.0 );
            _chunkBottom = (int) Math.floor( (double) bottom / 16.0 );
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

    public boolean chunkInBounds(int x, int z) {
        if(x >= _chunkLeft && x <= _chunkRight) {
            if( z >= _chunkTop && z <= _chunkBottom) {
                return true;
            }
        }
        return false;
    }

    public boolean blockInBounds(int x, int z) {
        if( x >= left && x <= right) {
            if(z >= top && z <= bottom) {
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

    @Override
    public String toString() {
        return "RenderBounds[shape="+shape+"; left(-x)="+_chunkLeft+"; top(-z)="+_chunkTop+"; right(x)="+_chunkRight+"; bottom(z)="+_chunkBottom+";]";
    }

    public enum RenderShape {
        RECT, CIRCLE
    }
}
