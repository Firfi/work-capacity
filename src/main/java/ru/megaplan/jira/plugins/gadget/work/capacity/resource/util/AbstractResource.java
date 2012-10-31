package ru.megaplan.jira.plugins.gadget.work.capacity.resource.util;

import com.atlassian.jira.rest.api.messages.TextMessage;
import com.atlassian.jira.rest.api.util.ValidationError;
import com.atlassian.jira.util.SimpleErrorCollection;

import java.util.Collection;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;


/**
 * Lightweight optional convenience base class for REST end points with commonly used methods.
 *
 * @since v4.0
 */
public abstract class AbstractResource
{
    /**
     * Creates an error response using the given errors.
     *
     * @param errors the errors to use for the error response. Should not be empty.
     * @return the error response.
     */
    protected Response createErrorResponse(final Collection<ValidationError> errors)
    {
        com.atlassian.jira.util.ErrorCollection errorCollection = new SimpleErrorCollection();
        for (ValidationError error : errors) {
            errorCollection.addError(error.getField(), error.getError());
        }
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        return Response.status(400).entity(ErrorCollection.Builder.newBuilder(errors).build()).cacheControl(cacheControl).build();  //entity(ErrorCollection.Builder.newBuilder(errors).build()).cacheControl()
    }

    protected Response createIndexingUnavailableResponse(String message) {
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        return Response.status(503).entity(ErrorCollection.Builder.newBuilder().addErrorMessage(message).build()).cacheControl(cacheControl).build();  // CacheControl(NO_CACHE)
    }

    protected Response createValidationResponse(Collection<ValidationError> errors)
    {
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        if (errors.isEmpty())
        {
            return Response.ok(new TextMessage("No input validation errors found.")).cacheControl(cacheControl).build();
        }
        else
        {
            return createErrorResponse(errors);
        }
    }
}