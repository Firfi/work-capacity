package ru.megaplan.jira.plugins.gadget.work.capacity.action.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/6/12
 * Time: 9:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class ChangeBean {
    private final String pkey;
    private final String field;
    private final String oldvalue;
    private final String newvalue;
    private final Timestamp created;
    private final long changegroupid;
    private final Timestamp issueCreated;



    public ChangeBean(ResultSet resultSet) throws SQLException {
        pkey = resultSet.getString(1);
        field = resultSet.getString(2);
        oldvalue = resultSet.getString(3);
        newvalue = resultSet.getString(4);
        created = resultSet.getTimestamp(5);
        changegroupid = resultSet.getLong(6);
        issueCreated = resultSet.getTimestamp(7);
    }

    public String getPkey() {
        return pkey;
    }

    public String getField() {
        return field;
    }

    public String getOldvalue() {
        return oldvalue;
    }

    public String getNewvalue() {
        return newvalue;
    }

    public Timestamp getCreated() {
        return created;
    }

    public long getChangegroupid() {
        return changegroupid;
    }

    public Timestamp getIssueCreated() {
        return issueCreated;
    }

    @Override
    public String toString() {
        return "ChangeBean{" +
                "pkey='" + pkey + '\'' +
                ", field='" + field + '\'' +
                ", oldvalue='" + oldvalue + '\'' +
                ", newvalue='" + newvalue + '\'' +
                ", created=" + created +
                ", changegroupid=" + changegroupid +
                '}';
    }
}
