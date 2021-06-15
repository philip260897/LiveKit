package at.livekit.storage;

import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import at.livekit.utils.Legacy;

public class StorageManager 
{
    public static IStorageAdapterGeneric initialize() throws Exception {
        IStorageAdapterGeneric storage = null;

        StorageSettings settings = Config.getStorageSettings();
        if(settings == null) throw new Exception("Storage Settings could not be read!");

        storage = getStorageAdapter(settings);
        storage.initialize();

        if(Config.getMigrateStorage() != null) {
            Plugin.log("Migration request detected. Migrating ["+Config.getMigrateStorage().getType()+"->"+settings.getType()+"]...");
            StorageThreadMarshallAdapter.DISABLE = true;
            migrateStorage(storage);
            StorageThreadMarshallAdapter.DISABLE = false;
            Config.resetMigration();
        }

        if(Legacy.hasLegacyStorage()) {
            StorageThreadMarshallAdapter.DISABLE = true;
            Plugin.log("Legacy Storage detected, converting!");
            Legacy.convertLegacyStorage();
            StorageThreadMarshallAdapter.DISABLE = false;
        }

        return storage;
    }

    private static void migrateStorage(IStorageAdapterGeneric target) throws Exception {
        if(!target.isEmpty()) throw new Exception("Target Storage is NOT empty. Migration only supported when target storage is empty.");

        IStorageAdapterGeneric source = getStorageAdapter(Config.getMigrateStorage());
        source.initialize();
        source.migrateTo(target);
    }

    private static IStorageAdapterGeneric getStorageAdapter(StorageSettings settings) throws Exception{
        IStorageAdapterGeneric storage = null;
        switch(settings.getType().toLowerCase()){
            case "json": storage = new JSONStorage(); break;
            case "sqlite": storage = new SQLStorage("jdbc:sqlite:"+Plugin.getInstance().getDataFolder().getPath()+"/storage.db"); break;
            case "mysql": storage = new SQLStorage("jdbc:mysql://"+settings.getHost(), settings.getUsername(), settings.getPassword()); break;
            case "postgresql": storage = new SQLStorage("jdbc:postgresql://"+settings.getHost(), settings.getUsername(), settings.getPassword()); break;
            default: throw new Exception(settings.getType()+" Not recognized! Try JSON, SQLITE, MYSQL or POSTGRESQL");
        }
        return storage;
    }

    public static class StorageSettings {
        private String type;
        private String host;
        private String username;
        private String password;
        
        public StorageSettings(String type, String host, String username, String password) {
            this.type = type;
            this.host = host;
            this.username = username;
            this.password = password;
        }

        public String getType() {
            return type;
        }

        public String getHost() {
            return host;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
