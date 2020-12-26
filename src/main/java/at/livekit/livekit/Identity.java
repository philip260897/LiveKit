package at.livekit.livekit;

public class Identity 
{
    private String uuid;
    private String name;
    private String authorization;
    
    public Identity(String uuid, String name, String authorization) {
        this.uuid = uuid;
        this.name = name;
        this.authorization = authorization;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getAuthorization() {
        return authorization;
    }
}
