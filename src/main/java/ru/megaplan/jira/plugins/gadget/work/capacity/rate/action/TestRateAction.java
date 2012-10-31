package ru.megaplan.jira.plugins.gadget.work.capacity.rate.action;

import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.web.action.JiraWebActionSupport;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 27.07.12
 * Time: 11:15
 * To change this template use File | Settings | File Templates.
 */
public class TestRateAction extends JiraWebActionSupport {

    String [] values;

    private final CustomFieldManager customFieldManager;
    private final IssueManager issueManager;

    TestRateAction(CustomFieldManager customFieldManager, IssueManager issueManager) {

        this.customFieldManager = customFieldManager;
        this.issueManager = issueManager;
    }

    @Override
    public String doDefault() {
        ArrayList<String> vals = new ArrayList<String>();
        vals.add("lol");
        CustomField rateField = customFieldManager.getCustomFieldObjectByName("ratefield");
        MutableIssue testIssue = issueManager.getIssueObject("TEST-11");
        issueManager.updateIssue(getLoggedInUser(),testIssue, EventDispatchOption.DO_NOT_DISPATCH,false);
        values = vals.toArray(new String[vals.size()]);
        return SUCCESS;
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }
}
