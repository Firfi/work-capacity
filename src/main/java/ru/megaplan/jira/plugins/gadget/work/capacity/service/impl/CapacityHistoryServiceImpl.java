package ru.megaplan.jira.plugins.gadget.work.capacity.service.impl;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.NotNull;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericDataSourceException;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.jdbc.ExecQueryCallbackFunctionIF;
import org.ofbiz.core.entity.jdbc.SQLProcessor;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.ChangeBean;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.ChangeGroupBean;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.StatusFact;
import ru.megaplan.jira.plugins.gadget.work.capacity.service.CapacityHistoryService;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 10.07.12
 * Time: 17:23
 * To change this template use File | Settings | File Templates.
 */
public class CapacityHistoryServiceImpl implements CapacityHistoryService {

    private final static Logger log = Logger.getLogger(CapacityHistoryServiceImpl.class);

    String ENTITY_DS = "defaultDS";
    private final IssueManager issueManager;
    private final SearchService searchService;
    private final UserManager userManager;
    private final String mainStatement = "select ji.pkey,ci.field, ci.oldvalue, ci.newvalue, cg.created, cg.id, ji.created as changegroupid from changegroup cg \n" +
            "join jiraissue ji on ji.id = cg.issueid " +
            "join changeitem ci on ci.groupid = cg.id " +
            "where ci.fieldtype = 'jira' " +
            "and ci.field in ('assignee','status') ";
    private final String orderStatement = " order by pkey, created ";
    private final int JQLFILTERLIMIT = 4000;

    private final static String STATUS = "status";
    private final static String ASSIGNEE = "assignee";

    private static final int MAX_AVAILABLE = 100;
    private final Semaphore semaphore;

    CapacityHistoryServiceImpl(IssueManager issueManager, SearchService searchService, UserManager userManager) {
        this.searchService = searchService;
        this.userManager = userManager;
        this.semaphore = new Semaphore(MAX_AVAILABLE);
        this.issueManager = issueManager;
    }

    @Override
    @NotNull
    public List<StatusFact> getStatusFacts(String issuesJqlQuery, String caller) {
        User callerUser = userManager.getUser(caller);
        Query q = parseQuery(callerUser,issuesJqlQuery);
        return getStatusFacts(q, callerUser);

    }

    private Query parseQuery(User callerUser, String issuesJqlQuery) {
        SearchService.ParseResult parseResult = searchService.parseQuery(callerUser, issuesJqlQuery);
        Query query = parseResult.getQuery();
        return query;
    }


    private String getInStatement(Query query, User callerUser) {
        StringBuilder sqlInStatement = new StringBuilder(" and ji.id in(");
        SearchResults searchResults = null;
        final String comma = ",";
        boolean foundSome = false;
        List<Issue> results = null;
        try {
            int i = 0;
            do {
                searchResults = searchService.search(callerUser, query, PagerFilter.newPageAlignedFilter(i,JQLFILTERLIMIT));
                i = i + JQLFILTERLIMIT;
                results = searchResults.getIssues();
                if (results.size() != 0) foundSome = true;
                for (Issue issue : results) {
                    sqlInStatement.append(issue.getId()).append(comma);
                }
            } while (results.size() != 0);
        } catch (SearchException e) {
            log.error("error in search", e);
            throw new RuntimeException(e);
        }



        if (foundSome) {
            sqlInStatement.deleteCharAt(sqlInStatement.length()-1);
        } else {
            return " and ji.id is null ";
        }
        sqlInStatement.append(")");
        return sqlInStatement.toString();
    }

    private String getStatus(ChangeBean cb, boolean newValue) {
        if (cb.getField().equals(STATUS)) {
            if (!newValue) return cb.getOldvalue();
            return cb.getNewvalue();
        }
        return null;
    }

    private String getAssignee(ChangeBean cb) {
        if (cb.getField().equals(ASSIGNEE)) return cb.getNewvalue();
        return null;
    }

    private boolean isNewChangegroup(ChangeBean cb, ChangeGroupBean pcgb) {
        return pcgb == null || pcgb.getGroupId() != cb.getChangegroupid();
    }

    private void fillNullStats(LinkedList<StatusFact> currentIssueStatusFacts, List<ChangeGroupBean> currentIssueChangeGroups) {
        if (currentIssueChangeGroups.size() == 0) {
            log.debug("currentIssueStatusFacts size : " + currentIssueStatusFacts.size());
            return;
        }
        boolean foundFirstChangeStatusInIssue = false;
        boolean foundFirstChangeAssigneeInIssue = false;
        String firstAssignee = null;
        String firstStatus = null;
        for (ChangeGroupBean cgb : currentIssueChangeGroups) {
            if (!foundFirstChangeAssigneeInIssue) {
                if (cgb.getOldAssignee() != null) {
                    foundFirstChangeAssigneeInIssue = true;
                    firstAssignee = cgb.getOldAssignee();
                }
            }
            if (!foundFirstChangeStatusInIssue) {
                if (cgb.getOldStatus() != null) {
                    foundFirstChangeStatusInIssue = true;
                    firstStatus = cgb.getOldStatus();
                }
            }
            if (foundFirstChangeAssigneeInIssue && foundFirstChangeStatusInIssue) break;
        }

        if (!(foundFirstChangeAssigneeInIssue && foundFirstChangeStatusInIssue)) {
            Issue i = issueManager.getIssueObject(currentIssueChangeGroups.get(0).getPkey());
            if (i == null) {
                log.debug("issue with pkey : " + currentIssueChangeGroups.get(0).getPkey() + " is not found");
            } else {
              //  log.warn("checking issue : " + i.getKey());
                try {
                    if (firstAssignee == null) firstAssignee = i.getAssignee().getName();
                    if (firstStatus == null) firstStatus = i.getStatusObject().getId();
                } catch (NullPointerException e) {
                    throw new NullPointerException(i.getKey() + " " + i.getAssignee());
                }

            }
        }
        StatusFact sf = new StatusFact(currentIssueChangeGroups.get(0).getOldAssignee(),
                currentIssueChangeGroups.get(0).getOldStatus(),
                currentIssueChangeGroups.get(0).getCreated().getTime()-currentIssueChangeGroups.get(0).getIssueCreated().getTime(),
                currentIssueChangeGroups.get(0).getPkey(),
                currentIssueChangeGroups.get(0).getIssueCreated(),
                false
                );
        currentIssueStatusFacts.push(sf);
        for (StatusFact statusFact : currentIssueStatusFacts) {
            if (statusFact.getAssignee() != null && statusFact.getStatus() != null) break;
            if (statusFact.getAssignee() == null) statusFact.setAssignee(firstAssignee);
            if (statusFact.getStatus() == null) statusFact.setStatus(firstStatus);
        }
    }

    private boolean isNewIssue(ChangeBean cb, ChangeBean pcb) {
        if (pcb == null) return true;
        return !(pcb.getPkey().equals(cb.getPkey()));
    }

    @Override
    public List<StatusFact> getStatusFacts(Query jqlQuery, User caller) {

        semaphore.acquireUninterruptibly();
        boolean aquired = true;
        SQLProcessor sqlProcessor = new SQLProcessor(ENTITY_DS);
        boolean processorIsClosed = false;

        final LinkedList<StatusFact> statusFacts = new LinkedList<StatusFact>();

        try {
            try {
                long currentTimeInMillis = System.currentTimeMillis();
                String issuesStatement = getInStatement(jqlQuery,caller);



                try {
                    sqlProcessor.execQuery(mainStatement + issuesStatement + orderStatement, new ExecQueryCallbackFunctionIF() {

                        LinkedList<StatusFact> currentIssueStatusFacts = new LinkedList<StatusFact>();
                        String currentAssignee = null;
                        String currentStatus = null;
                        List<ChangeGroupBean> currentIssueChangeGroups = new ArrayList<ChangeGroupBean>();
                        ChangeBean pcb = null;
                        ChangeGroupBean pcgb = null;
                        long executionTime = 0;
                        long currentTimeInMillis = System.currentTimeMillis();

                        @Override
                        public boolean processNextRow(ResultSet resultSet) throws SQLException {
                            long timeStart = System.currentTimeMillis();
                            ChangeBean cb = new ChangeBean(resultSet);
                            if (pcgb == null) {
                                pcgb = new ChangeGroupBean();
                            }
                            if (!resultSet.isFirst()) {
                                if (isNewIssue(cb,pcb)) {
                                    currentIssueChangeGroups.add(pcgb);
                                    currentIssueStatusFacts.add(new StatusFact(currentAssignee,
                                            currentStatus,
                                            currentTimeInMillis - pcgb.getCreated().getTime(),pcgb.getPkey(),
                                            pcgb.getCreated(),
                                            true
                                    ));
                                    fillNullStats(currentIssueStatusFacts, currentIssueChangeGroups);
                                    statusFacts.addAll(currentIssueStatusFacts);
                                    currentIssueStatusFacts.clear();
                                    currentIssueChangeGroups.clear();
                                    //flushStats();
                                    currentAssignee = null;
                                    currentStatus = null;
                                    //flushChangeGroup();
                                    pcgb = new ChangeGroupBean();
                                } else
                                if (isNewChangegroup(cb, pcgb)) {
                                    //addPreviousChangeGroup();
                                    currentIssueChangeGroups.add(pcgb);
                                    StatusFact sf = new StatusFact(currentAssignee,
                                            currentStatus,
                                            cb.getCreated().getTime() - pcgb.getCreated().getTime(),
                                            pcgb.getPkey(),
                                            pcgb.getCreated(),
                                            false);
                                    currentIssueStatusFacts.add(sf);
                                    //flushChangeGroup();
                                    pcgb = new ChangeGroupBean();
                                }
                            }
                            if (getAssignee(cb) != null) currentAssignee = getAssignee(cb);
                            if (getStatus(cb, true) != null) currentStatus = getStatus(cb, true);
                            //addChangeItem();
                            pcgb.addChangeBean(cb);
                            if (resultSet.isLast()) {
                                currentIssueChangeGroups.add(pcgb);
                                StatusFact sf = new StatusFact(currentAssignee,
                                        currentStatus,
                                        currentTimeInMillis - pcgb.getCreated().getTime(),
                                        pcgb.getPkey(),pcgb.getCreated(),
                                        true);
                                currentIssueStatusFacts.add(sf);
                                //fillNullStats();
                                fillNullStats(currentIssueStatusFacts, currentIssueChangeGroups);
                                //addGlobal();
                                statusFacts.addAll(currentIssueStatusFacts);
                                return true;
                            }
                            pcb = cb;
                            executionTime += System.currentTimeMillis() - timeStart;
                            if (executionTime > 15000) {
                                throw new RuntimeException("forever love here");
                            }
                            return true;
                        }
                    });
                } catch (GenericEntityException e) {
                    log.error("exception in execution query",e);
                    aquired = false;
                    semaphore.release();
                    sqlProcessor.close();
                    processorIsClosed = true;
                    return statusFacts;
                }

                sqlProcessor.close();
                processorIsClosed = true;

                List<Issue> issuesWithoutHistory = getIssuesWithoutHistory(jqlQuery, caller);
                if (issuesWithoutHistory != null && issuesWithoutHistory.size() > 0) {
                    addCreatedFacts(statusFacts, issuesWithoutHistory, currentTimeInMillis);
                }

                semaphore.release();
                aquired = false;

            } finally {
                if (aquired)
                    semaphore.release();
                if (!processorIsClosed)
                    sqlProcessor.close();
            }
        } catch (GenericDataSourceException e) {
            log.error("error in closing processor",e);
        }
        return statusFacts;
    }

    private List<Issue> getIssuesWithoutHistory(Query jqlQuery, User caller) {
        String whereString = searchService.getJqlString(JqlQueryBuilder.newClauseBuilder(jqlQuery.getWhereClause()).buildQuery()) +
                " and not (status changed or assignee changed)";
        SearchService.ParseResult parseResult = searchService.parseQuery(caller, whereString);
        JqlQueryBuilder jqlQueryBuilder = null;
        if (parseResult.isValid()) {
            jqlQueryBuilder = JqlQueryBuilder.newBuilder(parseResult.getQuery());
        } else {
            throw new RuntimeException("parse result is not valid");
        }
        try {
            return searchService.search(caller, jqlQueryBuilder.buildQuery(), PagerFilter.getUnlimitedFilter()).getIssues();
        } catch (SearchException e) {
            log.error("search exception,e");
        }
        return null;
    }

    private void addCreatedFacts(LinkedList<StatusFact> statusFacts, List<Issue> issuesWithoutHistory, long currentTimeInMillis) {
        for (Issue issue : issuesWithoutHistory) {
            statusFacts.add(new StatusFact(issue.getAssignee()!=null?issue.getAssignee().getName():"NULL",
                    issue.getStatusObject().getId(),
                    currentTimeInMillis - issue.getCreated().getTime(),
                    issue.getKey(),
                    issue.getCreated(),
                    true));
        }
    }
}
