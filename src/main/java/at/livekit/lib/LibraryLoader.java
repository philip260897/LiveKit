package at.livekit.lib;

import at.livekit.plugin.Plugin;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;

public class LibraryLoader {
    
    final static LibraryObject ORMLITE = new LibraryObject("com{}j256{}ormlite", "ormlite-core", "5.5", "ormlite-core", "com{}j256{}ormlite", "at{}lindnerdev{}libs{}com{}j256{}ormlite");
    final static LibraryObject ORMLITE_JDBC = new LibraryObject("com{}j256{}ormlite", "ormlite-jdbc", "5.5", "ormlite-jdbc", "com{}j256{}ormlite", "at{}lindnerdev{}libs{}com{}j256{}ormlite");

    final static LibraryObject APACHE_LOGGING = new LibraryObject("org{}apache{}logging{}log4j", "log4j-core", "2.22.1", "log4j-core", "org{}apache{}logging{}log4j{}core", "at{}lindnerdev{}libs{}org{}apache{}logging{}log4j{}core");
    final static LibraryObject POSTGRESQL = new LibraryObject("org{}postgresql", "postgresql", "42.3.9", "postgresql", "org{}postgresql", "at{}lindnerdev{}libs{}org{}postgresql");
    final static LibraryObject JSON = new LibraryObject("org{}json", "json", "20240303", "json", "org{}json", "at{}lindnerdev{}libs{}org{}json");
    final static LibraryObject JSOUP = new LibraryObject("org{}jsoup", "jsoup", "1.17.2", "jsoup", "org{}jsoup", "at{}lindnerdev{}libs{}org{}jsoup");
    final static LibraryObject ZSTD = new LibraryObject("com{}github{}luben", "zstd-jni", "1.5.5-1", "zstd-jni", "com{}github{}luben{}zstd", "com{}github{}luben{}zstd");
    final static LibraryObject NBT = new LibraryObject("com{}github{}Querz", "NBT", "6.1", "nbt", "net{}querz", "at{}lindnerdev{}libs{}com{}github{}Querz");

    final static LibraryObject[] LIBRARIES = new LibraryObject[] {
        ORMLITE,
        ORMLITE_JDBC,
        APACHE_LOGGING,
        POSTGRESQL,
        JSON,
        JSOUP,
        ZSTD,
        NBT
    };


    public static void loadLibraries(Plugin plugin) {
        BukkitLibraryManager bukkitLibraryManager = new BukkitLibraryManager(plugin);
        bukkitLibraryManager.addMavenCentral();
        bukkitLibraryManager.addRepository("https://jitpack.io");


        for(LibraryObject libraryObject : LIBRARIES) {
            Library library = createLibrary(libraryObject);
            bukkitLibraryManager.loadLibrary(library);
        }
    }

    private static Library createLibrary(LibraryObject libraryObject) {
        return Library.builder()
            .groupId(libraryObject.getGroupId())
            .artifactId(libraryObject.getArtifactId())
            .version(libraryObject.getVersion())
            //.id(libraryObject.getId())
            //.relocate(libraryObject.getOldRelocation(), libraryObject.getNewRelocation())
            .build();
    }
}
