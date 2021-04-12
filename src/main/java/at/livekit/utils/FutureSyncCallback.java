package at.livekit.utils;

public interface FutureSyncCallback<T> {
    
    public void onSyncResult(T result);
}
