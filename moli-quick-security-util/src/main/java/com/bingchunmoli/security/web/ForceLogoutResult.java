package com.bingchunmoli.security.web;

/**
 * Force logout result.
 *
 * @author MoLi
 */
public class ForceLogoutResult {

    private String target;

    private int expiredSessions;

    public ForceLogoutResult() {
    }

    public ForceLogoutResult(String target, int expiredSessions) {
        this.target = target;
        this.expiredSessions = expiredSessions;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getExpiredSessions() {
        return expiredSessions;
    }

    public void setExpiredSessions(int expiredSessions) {
        this.expiredSessions = expiredSessions;
    }
}
