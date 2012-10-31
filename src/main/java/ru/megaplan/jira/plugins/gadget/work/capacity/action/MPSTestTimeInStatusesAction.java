package ru.megaplan.jira.plugins.gadget.work.capacity.action;

import com.atlassian.core.util.map.EasyMap;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.crowd.password.factory.PasswordEncoderFactory;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.changehistory.ChangeHistoryItem;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.OrderableField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.issue.util.IssueChangeHolder;
import com.atlassian.jira.ofbiz.OfBizDelegator;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.workflow.WorkflowException;
import com.atlassian.security.password.DefaultPasswordEncoder;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.EntityCondition;
import org.ofbiz.core.entity.GenericValue;
import org.ofbiz.core.entity.jdbc.SQLProcessor;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.ChangeBean;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.ChangeGroupBean;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.StatusFact;
import ru.megaplan.jira.plugins.gadget.work.capacity.service.CapacityHistoryService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 06.07.12
 * Time: 16:11
 * To change this template use File | Settings | File Templates.
 */
public class MPSTestTimeInStatusesAction extends JiraWebActionSupport {

    private final static Logger log = Logger.getLogger(MPSTestTimeInStatusesAction.class);


    private final CapacityHistoryService capacityHistoryService;

    private StatusFact[] results;
    private String logValue;

    MPSTestTimeInStatusesAction(CapacityHistoryService capacityHistoryService) {
        this.capacityHistoryService = capacityHistoryService;
    }

    @Override
    public String doDefault() throws Exception {
        //log.warn("start test action");
        for (int i = 0; i < 50; ++i) {
            capacityHistoryService.getStatusFacts("project = MPS",getLoggedInUser().getName());
        }
        List<StatusFact> statusFacts = capacityHistoryService.getStatusFacts("project = MPS",getLoggedInUser().getName());
       // log.warn("capacity history service done");
        results = statusFacts.toArray(new StatusFact[statusFacts.size()]);
        return SUCCESS;
    }

//    private void clearResolution() throws SearchException, IndexException {
//        User mp = userManager.getUser("megaplan");
//        SearchService.ParseResult parseResult = searchService.parseQuery(mp, "project = MPS and resolution is not empty and status != 6");
//        SearchResults searchResults = searchService.search(mp, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
//        List<Issue> issues = searchResults.getIssues();
//        for (Issue issue : issues) {
//            MutableIssue mi = issueManager.getIssueObject(issue.getId());
//            mi.setResolutionObject(null);
//            issueManager.updateIssue(mp,mi,EventDispatchOption.DO_NOT_DISPATCH,false);
//        }
//
//    }
    @Override
    public String doExecute() throws Exception {
        return doDefault();
    }

    public StatusFact[] getResults() {
        return results;
    }

    public String getLogValue() {
        return logValue;
    }
}
