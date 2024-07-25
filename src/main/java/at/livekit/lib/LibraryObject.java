package at.livekit.lib;

public class LibraryObject {
    
    final String groupId;
    final String artifactId;
    final String version;
    final String id;
    final String oldRelocation;
    final String newRelocation;

    public LibraryObject(String groupId, String artifactId, String version, String id, String oldRelocation, String newRelocation) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.id = id;
        this.oldRelocation = oldRelocation;
        this.newRelocation = newRelocation;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getId() {
        return id;
    }

    public String getOldRelocation() {
        return oldRelocation;
    }

    public String getNewRelocation() {
        return newRelocation;
    }
}
