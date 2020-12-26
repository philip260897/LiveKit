package at.livekit.main;

public class LiveEntity extends LiveSyncable {
    
    protected String head;
    protected String name;

    protected double x = 0;
    protected double y = 0;
    protected double z = 0;
    
    protected double health = 0;
    protected int armor = 0;
    protected double exhaustion = 0;

    public LiveEntity(String uuid, String name, String head) {
        super(uuid);
        this.name = name;
        this.head = head;
        this.markDirty("name", "head");
    }

    public void updateLocation(double x, double y, double z){
        this.x = x;
        this.y = y;
        this.z = z;
        this.markDirty("x", "y", "z");
    }

    public void updateHead(String head) {
        this.head = head;
        this.markDirty("head");
    }

    public void updateArmor(int armor) {
        this.armor = armor;
        this.markDirty("armor");
    }

    public void updateHealth(double health) {
        this.health = health;
        this.markDirty("health");
    }

    public void updateExhaustion(double exhaustion) {
        this.exhaustion = exhaustion;
        this.markDirty("exhaustion");
    }
}
