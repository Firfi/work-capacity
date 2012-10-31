package ru.megaplan.jira.plugins.gadget.work.capacity.resource;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.util.UserManager;
import ru.megaplan.jira.plugins.gadget.work.capacity.resource.util.AbstractResource;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 10.07.12
 * Time: 19:08
 * To change this template use File | Settings | File Templates.
 */
@Path("/assignee")
public class AssigneeResource extends  AbstractResource {

    private final UserManager userManager;
    private final GroupManager groupManager;
    private final static String JIRAUSERS = "jira-users";

    public AssigneeResource(UserManager userManager, GroupManager groupManager) {
        this.userManager = userManager;
        this.groupManager = groupManager;
    }

    @GET
    @Path ("/allActive")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAllStatuses(@Context HttpServletRequest request) {
        Collection<User> users = groupManager.getUsersInGroup(JIRAUSERS);
        Group robots = groupManager.getGroup("mps-robots");
        if (robots!= null) users.addAll(groupManager.getUsersInGroup(robots));
        Collection<Assignee> result = new ArrayList<Assignee>();
        result.add(new Assignee("NULL","ALL"));
        for (User user : users) {
            if (user.isActive() || groupManager.isUserInGroup(user, robots)) {
                StringBuilder stringBuilder = new StringBuilder(user.getDisplayName());
                stringBuilder.append(' ').append('[').append(user.getName()).append(']');
                result.add(new Assignee(user.getName(),stringBuilder.toString()));
            }
        }
        return Response.ok(result).cacheControl(CreatedVsResolvedResource.noCache()).build();
    }

}
