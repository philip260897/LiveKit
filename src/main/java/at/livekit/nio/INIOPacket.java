package at.livekit.nio;

public interface INIOPacket {
    public byte[] data();

    public byte[] header();

    public boolean hasHeader();

    public boolean supportsCompression();
}