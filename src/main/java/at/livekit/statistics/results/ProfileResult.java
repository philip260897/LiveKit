package at.livekit.statistics.results;

import org.json.JSONObject;

public class ProfileResult {
    private Long totalTimePlayed;
    private Long totalSessions;
    private Long longestSession;
    private Long totalDeaths;
    private Long mostDeathsPerDay;
    private Long totalPVPKills;
    private Long totalPVEKills;

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
    public Long getMostDeathsPerDay() {
        return mostDeathsPerDay;
    }
    public void setMostDeathsPerDay(Long mostDeathsPerDay) {
        this.mostDeathsPerDay = mostDeathsPerDay;
    }
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

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("totalTimePlayed", totalTimePlayed);
        json.put("totalSessions", totalSessions);
        json.put("longestSession", longestSession);
        json.put("totalDeaths", totalDeaths);
        json.put("mostDeathsPerDay", mostDeathsPerDay);
        json.put("totalPVPKills", totalPVPKills);
        json.put("totalPVEKills", totalPVEKills);
        return json;
    }
}
