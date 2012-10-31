package ru.megaplan.jira.plugins.gadget.work.capacity.action;

import com.atlassian.jira.web.action.JiraWebActionSupport;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 05.09.12
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public class CommonTestAction extends JiraWebActionSupport {

    String result;

    @Override
    public String doDefault() throws Exception {
        result = Thread.getAllStackTraces().toString();
        return SUCCESS;
    }

    @Override
    public String doExecute() throws Exception {
        return doDefault();
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
