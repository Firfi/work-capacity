package ru.megaplan.jira.plugins.gadget.work.capacity.action.util;

import java.sql.Timestamp;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/7/12
 * Time: 1:55 AM
 * To change this template use File | Settings | File Templates.
 */
public class ChangeGroupBean {

    private final static String STATUS = "status";

    private String oldStatus;
    private String newStatus;
    private long groupId;
    private String oldAssignee;
    private String newAssignee;
    private Timestamp created;
    private String pkey;
    private Timestamp issueCreated;

    public ChangeGroupBean() {
    }

    public void addChangeBean(ChangeBean changeBean) {
        groupId = changeBean.getChangegroupid();
        created = changeBean.getCreated();
        pkey = changeBean.getPkey();
        issueCreated = changeBean.getIssueCreated();
        if (STATUS.equals(changeBean.getField())) {
            oldStatus = changeBean.getOldvalue();
            newStatus = changeBean.getNewvalue();
        } else {  //assume that it is assignee
            oldAssignee = changeBean.getOldvalue();
            newAssignee = changeBean.getNewvalue();
        }
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getOldAssignee() {
        return oldAssignee;
    }

    public String getNewAssignee() {
        return newAssignee;
    }

    public Timestamp getCreated() {
        return created;
    }

    public String getPkey() {
        return pkey;
    }

    public Timestamp getIssueCreated() {
        return issueCreated;
    }

    public void setIssueCreated(Timestamp issueCreated) {
        this.issueCreated = issueCreated;
    }
}
