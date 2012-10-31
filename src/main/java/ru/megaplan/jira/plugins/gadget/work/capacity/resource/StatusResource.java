package ru.megaplan.jira.plugins.gadget.work.capacity.resource;

import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import ru.megaplan.jira.plugins.gadget.work.capacity.resource.util.AbstractResource;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 10.07.12
 * Time: 19:08
 * To change this template use File | Settings | File Templates.
 */
@Path("/status")
public class StatusResource extends  AbstractResource {

    private final StatusManager statusManager;


    public StatusResource(StatusManager statusManager) {
        this.statusManager = statusManager;
    }

    @GET
    @Path ("/all")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAllStatuses(@Context HttpServletRequest request) {
        List<Status> responseList = new ArrayList<Status>();
        responseList.add(new Status("0","ALL"));
        for (com.atlassian.jira.issue.status.Status status : statusManager.getStatuses()) {
            responseList.add(new Status(status.getId(), status.getName()));
        }
        return Response.ok(responseList).cacheControl(CreatedVsResolvedResource.noCache()).build();


    }

}
