package ru.megaplan.jira.plugins.gadget.work.capacity.service;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.query.Query;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.StatusFact;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 10.07.12
 * Time: 17:21
 * To change this template use File | Settings | File Templates.
 */
public interface CapacityHistoryService {
    List<StatusFact> getStatusFacts(String issuesJqlQuery, String caller);
    List<StatusFact> getStatusFacts(Query jqlQuery, User caller);
}
