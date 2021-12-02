package at.livekit.statistics.results;

import java.util.UUID;

import org.json.JSONObject;

public class ProfileResult {
    private Long firstSeen;
    private Long lastSeen;
    private Long totalTimePlayed;
    private Long totalSessions;
    private Long longestSession;
    private Long totalDeaths;
    //private Long mostDeathsPerDay;
    private Long totalPVPKills;
    private Long totalPVEKills;
    private UUID lastKillPVPTarget;
    private Long lastKillPVPTimestamp;
    //private Long lastKillPVETarget;
    //private Long lastKillPVETimestamp;
    private Long mostUsedWeapon;
    private Long mostUsedWeaponKills;
    private Long mostUsedTool;
    private Long mostUsedToolValue;
    private Long mostFarmedBlock;
    private Long mostFarmedBlockValue;

    public Long getTotalTimePlayed() {
        return totalTimePlayed;
    }
    public void setTotalTimePlayed(Long totalTimePlayed) {
        this.totalTimePlayed = totalTimePlayed;
    }
    public Long getTotalSessions() {
        return totalSessions;
    }
    public void setTotalSessions(Long totalSessions) {
        this.totalSessions = totalSessions;
    }
    public Long getLongestSession() {
        return longestSession;
    }
    public void setLongestSession(Long longestSession) {
        this.longestSession = longestSession;
    }
    public Long getTotalDeaths() {
        return totalDeaths;
    }
    public void setTotalDeaths(Long totalDeaths) {
        this.totalDeaths = totalDeaths;
    }
    /*public Long getMostDeathsPerDay() {
        return mostDeathsPerDay;
    }
    public void setMostDeathsPerDay(Long mostDeathsPerDay) {
        this.mostDeathsPerDay = mostDeathsPerDay;
    }*/
    public Long getTotalPVPKills() {
        return totalPVPKills;
    }
    public void setTotalPVPKills(Long totalPVPKills) {
        this.totalPVPKills = totalPVPKills;
    }
    public Long getTotalPVEKills() {
        return totalPVEKills;
    }
    public void setTotalPVEKills(Long totalPVEKills) {
        this.totalPVEKills = totalPVEKills;
    } 
    public Long getMostUsedWeapon() {
        return mostUsedWeapon;
    }
    public void setMostUsedWeapon(Long mostUsedWeapon) {
        this.mostUsedWeapon = mostUsedWeapon;
    }
    public Long getMostUsedWeaponKills() {
        return mostUsedWeaponKills;
    }
    public void setMostUsedWeaponKills(Long mostUsedWeaponKills) {
        this.mostUsedWeaponKills = mostUsedWeaponKills;
    }
    public Long getMostFarmedBlock() {
        return mostFarmedBlock;
    }
    public void setMostFarmedBlock(Long mostFarmedBlock) {
        this.mostFarmedBlock = mostFarmedBlock;
    }
    public Long getMostFarmedBlockValue() {
        return mostFarmedBlockValue;
    }
    public void setMostFarmedBlockValue(Long mostFarmedBlockValue) {
        this.mostFarmedBlockValue = mostFarmedBlockValue;
    }
    public UUID getLastKillPVPTarget() {
        return lastKillPVPTarget;
    }
    public void setLastKillPVPTarget(UUID lastKillPVPTarget) {
        this.lastKillPVPTarget = lastKillPVPTarget;
    }
    public Long getLastKillPVPTimestamp() {
        return lastKillPVPTimestamp;
    }
    public void setLastKillPVPTimestamp(Long lastKillPVPTimestamp) {
        this.lastKillPVPTimestamp = lastKillPVPTimestamp;
    }
    /*public Long getLastKillPVETarget() {
        return lastKillPVETarget;
    }
    public void setLastKillPVETarget(Long lastKillPVETarget) {
        this.lastKillPVETarget = lastKillPVETarget;
    }
    public Long getLastKillPVETimestamp() {
        return lastKillPVETimestamp;
    }
    public void setLastKillPVETimestamp(Long lastKillPVETimestamp) {
        this.lastKillPVETimestamp = lastKillPVETimestamp;
    }*/
    public Long getMostUsedTool() {
        return mostUsedTool;
    }
    public void setMostUsedTool(Long mostUsedTool) {
        this.mostUsedTool = mostUsedTool;
    }
    public Long getMostUsedToolValue() {
        return mostUsedToolValue;
    }
    public void setMostUsedToolValue(Long mostUsedToolValue) {
        this.mostUsedToolValue = mostUsedToolValue;
    }
    
    public Long getFirstSeen() {
        return firstSeen;
    }
    public void setFirstSeen(Long firstSeen) {
        this.firstSeen = firstSeen;
    }
    public Long getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(Long lastSeen) {
        this.lastSeen = lastSeen;
    }
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("firstSeen", firstSeen);
        json.put("lastSeen", lastSeen);
        json.put("totalTimePlayed", totalTimePlayed);
        json.put("totalSessions", totalSessions);
        json.put("longestSession", longestSession);
        json.put("totalDeaths", totalDeaths);
        //json.put("mostDeathsPerDay", mostDeathsPerDay);
        json.put("totalPVPKills", totalPVPKills);
        json.put("totalPVEKills", totalPVEKills);
        json.put("mostUsedWeapon", mostUsedWeapon);
        json.put("mostUsedWeaponKills", mostUsedWeaponKills);
        json.put("mostUsedTool", mostUsedTool);
        json.put("mostUsedToolValue", mostUsedToolValue);
        json.put("mostFarmedBlock", mostFarmedBlock);
        json.put("mostFarmedBlockValue", mostFarmedBlockValue);
        json.put("lastKillPVPTarget", lastKillPVPTarget);
        json.put("lastKillPVPTimestamp", lastKillPVPTimestamp);
        //json.put("lastKillPVETarget", lastKillPVETarget);
        //json.put("lastKillPVETimestamp", lastKillPVETimestamp);
        return json;
    }
}
