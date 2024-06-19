package at.livekit.nio.proxy;

public interface NIOProxyListener<T> {
    public void clientConnected(NIOProxyClient<T> client);
    public void clientDisconnected(NIOProxyClient<T> client);
}
