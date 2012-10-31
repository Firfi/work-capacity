package ru.megaplan.jira.plugins.gadget.work.capacity.action.util;

import org.apache.log4j.Logger;

import java.sql.Timestamp;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/6/12
 * Time: 9:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class StatusFact {

    private final static Logger log = Logger.getLogger(StatusFact.class);

    private String pkey;



    private String assignee;
    private String status;
    private long time;
    private Timestamp when;
    private boolean isActive;

    public StatusFact(String assignee, String status, long time, String pkey, Timestamp when, boolean isActive ) {
        this.assignee = assignee;
        this.status = status;
        this.time = time;
        this.pkey = pkey;
        this.when = when;
        this.isActive = isActive;
    }

    public StatusFact(StatusFact other) {
        this.assignee = other.assignee;
        this.status = other.status;
        this.time = other.time;
        this.when = new Timestamp(other.when.getTime());
        this.pkey = other.pkey;
        this.isActive = other.isActive;
    }

    public String getPkey() {
        return pkey;
    }

    public void setPkey(String pkey) {
        this.pkey = pkey;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Timestamp getWhen() {
        return when;
    }

    public void setWhen(Timestamp when) {
        this.when = when;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }



    public String toString() {
        return "StatusFact " + pkey + ": assignee : " + assignee + "; status : " + status + "; time : " + (time/1000/60==0?time/1000+"s":time/1000/60+"m") + " " + time + " and when : " + when.getTime() + " so end is : " + (when.getTime()+time) + " and it is active now : " + isActive;
    }

}
