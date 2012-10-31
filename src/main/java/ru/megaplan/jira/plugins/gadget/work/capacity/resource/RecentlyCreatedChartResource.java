
package ru.megaplan.jira.plugins.gadget.work.capacity.resource;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.charts.Chart;
import com.atlassian.jira.charts.ChartFactory;
import com.atlassian.jira.charts.util.ChartUtils;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.issue.index.SearchUnavailableException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.rest.api.util.ValidationError;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.util.profiling.UtilTimerStack;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.log4j.Logger;
import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.data.category.CategoryDataset;
import ru.megaplan.jira.plugins.gadget.work.capacity.resource.util.ResourceDateValidator;
import ru.megaplan.jira.plugins.gadget.work.capacity.resource.util.SearchQueryBackedResource;
import ru.megaplan.jira.plugins.gadget.work.capacity.service.CapacityHistoryService;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * REST endpoint to validate and retreive a Recent Created chart.
 *
 * @since v4.0
 */
@Path ("/capacityHistory")
@AnonymousAllowed
@Produces ({ MediaType.APPLICATION_JSON })
public class RecentlyCreatedChartResource extends SearchQueryBackedResource
{

    private final static Logger log = Logger.getLogger(RecentlyCreatedChartResource.class);

    private static final String PERIOD_NAME = "periodName";
    private static final String NUM_ISSUES = "numIssues";
    private static final String DAYS_BEFORE_NAME = "daysprevious";
    private static final String DAYS_AFTER_NAME = "daysafter";
    private static final String SHOW_UNRESOLVED_TREND = "showUnresolvedTrend";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String STATUSES_IN = "statusesIn";
    private static final String ASSIGNEES = "assignees";
    private static final String MERGE_STATUSES = "mergeStatuses";
    private static final String COUNT_OLD_FACTS = "countOldFacts";

    private final ChartFactory chartFactory;
    private static final String RETURN_DATA = "returnData";
    private ResourceDateValidator resourceDateValidator;

    private final SearchProvider searchProvider;
    private final IssueIndexManager issueIndexManager;
    private final ApplicationProperties applicationProperties;
    private final TimeZoneManager timeZoneManager;
    private final CapacityHistoryService capacityHistoryService;



    public RecentlyCreatedChartResource(final ChartUtils chartUtils, final JiraAuthenticationContext authenticationContext,
                                        final SearchService searchService, final PermissionManager permissionManager,
                                        final ChartFactory chartFactory, final VelocityRequestContextFactory velocityRequestContextFactory,
                                        final ApplicationProperties applicationProperties, SearchProvider searchProvider, IssueIndexManager issueIndexManager, ApplicationProperties applicationProperties1, TimeZoneManager timeZoneManager, CapacityHistoryService capacityHistoryService)
    {
        super(chartUtils, authenticationContext, searchService, permissionManager, velocityRequestContextFactory);
        this.chartFactory = chartFactory;
        this.applicationProperties = applicationProperties;
        this.searchProvider = searchProvider;
        this.issueIndexManager = issueIndexManager;
        this.timeZoneManager = timeZoneManager;
        this.capacityHistoryService = capacityHistoryService;
        resourceDateValidator = new ResourceDateValidator(applicationProperties);
    }

    /**
     * Generate a Recently Created Chart and returns a simple bean containing all relevent information
     *
     * @param request     The current HTTPRequest. Needed for url generation
     * @param queryString a filter id (starts with "filter-") or project id (starts with "project-")or jql (starts with
     *                    "jql-")
     * @param periodName  The name of the period.  See - {@link com.atlassian.jira.charts.ChartFactory.PeriodName}
     * @param width       the width of the chart in pixels (defaults to 400px)
     * @param height      the height of the chart in pixels (defaults to 250px)
     */
    @GET
    @Path ("/generate")
    public Response generateChart(@Context HttpServletRequest request,
                                  @QueryParam (QUERY_STRING) String queryString,
                                  @QueryParam (DAYS_BEFORE_NAME) @DefaultValue ("30") final String daysBefore,
                                  @QueryParam (DAYS_AFTER_NAME) @DefaultValue ("0") final String daysAfter,
                                  @QueryParam (PERIOD_NAME) @DefaultValue ("daily") final String periodName,
                                  @QueryParam (SHOW_UNRESOLVED_TREND) @DefaultValue ("false") final boolean showUnresolvedTrend,
                                  @QueryParam (RETURN_DATA) @DefaultValue ("false") final boolean returnData,
                                  @QueryParam (WIDTH) @DefaultValue ("400") final int width,
                                  @QueryParam (HEIGHT) @DefaultValue ("250") final int height,
                                  @QueryParam(STATUSES_IN) final String statusesInString,
                                  @QueryParam(ASSIGNEES) final String assigneesString,
                                  @QueryParam (MERGE_STATUSES) @DefaultValue ("true") final boolean mergeStatuses,
                                  @QueryParam (COUNT_OLD_FACTS) @DefaultValue("false") final boolean countOldFacts)
    {
        final Collection<ValidationError> errors = new ArrayList<ValidationError>();

        final User user = authenticationContext.getLoggedInUser();
        final SearchRequest searchRequest;

        Map<String, Object> params = new HashMap<String, Object>();

        // validate input
        searchRequest = getSearchRequestAndValidate(queryString, errors, params);
        final ChartFactory.PeriodName period = resourceDateValidator.validatePeriod(PERIOD_NAME, periodName, errors);
        final int startDays = resourceDateValidator.validateDaysPrevious(DAYS_BEFORE_NAME, period, daysBefore, errors);

        final Set<String> statusesIn = CreatedVsResolvedResource.getStatusesFromString(statusesInString);
        final Set<String> assignees = CreatedVsResolvedResource.validateAssignees(assigneesString, errors);

        if (!errors.isEmpty())
        {
            return createErrorResponse(errors);
        }

        final ChartFactory.ChartContext context = new ChartFactory.ChartContext(user, searchRequest, width, height);
        try
        {


            final Chart chart = generateCapacityHistoryChart(context, startDays, Integer.parseInt(daysAfter), period, showUnresolvedTrend,statusesIn,assignees,mergeStatuses, countOldFacts);
            //chartFactory.generateRecentlyCreated(context, validatedDays, period);

            final String location = chart.getLocation();
            final String title = getFilterTitle(params);
            final String filterUrl = getFilterUrl(params);
            final Integer issueCount = (Integer) chart.getParameters().get(NUM_ISSUES);
            final String imageMap = chart.getImageMap();
            final String imageMapName = chart.getImageMapName();
            final Integer imageHeight = (Integer) chart.getParameters().get(HEIGHT);
            final Integer imageWidth = (Integer) chart.getParameters().get(WIDTH);

            DataRow[] data = null;
            if (returnData)
            {
                final CategoryDataset completeDataset = (CategoryDataset) chart.getParameters().get("completeDataset");
                final CategoryURLGenerator completeUrlGenerator = (CategoryURLGenerator) chart.getParameters().get("completeDatasetUrlGenerator");

                data = generateDataset(completeDataset, completeUrlGenerator);
            }

            final RecentlyCreatedChart recentlyCreatedChart = new RecentlyCreatedChart(location, title, filterUrl, imageMap, imageMapName, issueCount, imageWidth, imageHeight, data);

            return Response.ok(recentlyCreatedChart).cacheControl(noCache()).build();
        }
        catch (SearchUnavailableException e)
        {
            if (!e.isIndexingEnabled())
            {
                return createIndexingUnavailableResponse(createIndexingUnavailableMessage());
            }
            else
            {
                throw e;
            }
        }
    }

    private Chart generateCapacityHistoryChart(ChartFactory.ChartContext context, int daysBefore, int daysAfter, ChartFactory.PeriodName period, boolean showUnresolvedTrend, Set<String> statusesIn, Set<String> assignees, boolean mergeStatuses, boolean countOldFacts) {
        UtilTimerStack.push("Generating Capacity History Chart");
        try {
            final ru.megaplan.jira.plugins.gadget.work.capacity.chart.RecentlyCreatedChart recentlyCreatedChartFromFactory =
                    new ru.megaplan.jira.plugins.gadget.work.capacity.chart.RecentlyCreatedChart
                            (searchProvider, issueIndexManager, searchService, applicationProperties, timeZoneManager, capacityHistoryService);
            return recentlyCreatedChartFromFactory.generateChart(context.getRemoteUser(),
                    context.getSearchRequest(),
                    daysBefore,
                    daysAfter,
                    period,
                    showUnresolvedTrend,
                    context.getWidth(),
                    context.getHeight(),
                    statusesIn,
                    assignees,
                    mergeStatuses,
                    countOldFacts);
        } finally {
            UtilTimerStack.pop("Generating Capacity History Chart");
        }

    }

    private DataRow[] generateDataset(CategoryDataset dataset, CategoryURLGenerator urlGenerator)
    {
        final DataRow[] data = new DataRow[dataset.getColumnCount()];
        // header
        for (int col = 0; col < dataset.getColumnCount(); col++)
        {
            Object key = dataset.getColumnKey(col);
            int unresolvedVal = dataset.getValue(0, col).intValue();
            String unresolvedUrl = urlGenerator.generateURL(dataset, 0, col);
            int resolvedVal = dataset.getValue(1, col).intValue();
            String resolvedUrl = urlGenerator.generateURL(dataset, 1, col);
            int totalCreatedVal = unresolvedVal + resolvedVal;

            data[col] = new DataRow(key, totalCreatedVal, resolvedVal, resolvedUrl, unresolvedVal, unresolvedUrl);
        }

        return data;
    }

    /**
     * Ensures all parameters are valid for the Recently Created Chart
     *
     * @param queryString a filter id (starts with "filter-") or project id (starts with "project-").
     * @param days        The number of days previous to go back for.  Must be positive.
     * @param periodName  The name of the period.  See - {@link com.atlassian.jira.charts.ChartFactory.PeriodName}
     */
    @GET
    @Path ("/validate")
    public Response validateChart(@QueryParam (QUERY_STRING) String queryString,
                                  @QueryParam (DAYS_BEFORE_NAME) @DefaultValue ("30") final String days,
                                  @QueryParam (PERIOD_NAME) @DefaultValue ("daily") final String periodName)
    {
        final Collection<ValidationError> errors = new ArrayList<ValidationError>();

        getSearchRequestAndValidate(queryString, errors, new HashMap<String, Object>());
        final ChartFactory.PeriodName period = resourceDateValidator.validatePeriod(PERIOD_NAME, periodName, errors);
        resourceDateValidator.validateDaysPrevious(DAYS_BEFORE_NAME, period, days, errors);

        return createValidationResponse(errors);
    }

    ///CLOVER:OFF
    /**
     * A simple bean contain all information required to render the Recently Created Chart
     */
    @XmlRootElement
    public static class RecentlyCreatedChart
    {
        // The URL where the chart image is available from.  The image is once of image that can only be accessed once.
        @XmlElement
        private String location;
        // The title of the chart
        @XmlElement
        private String filterTitle;
        // The link of where to send the user to - For a project, send em to the browse project, for a filter, send em tothe Issue Nav
        @XmlElement
        private String filterUrl;
        @XmlElement
        private String imageMap;
        @XmlElement
        private String imageMapName;
        @XmlElement
        private Integer issueCount;
        @XmlElement
        private DataRow[] data;
        @XmlElement
        private Integer height;
        @XmlElement
        private Integer width;

        @SuppressWarnings ({ "UnusedDeclaration", "unused" })
        RecentlyCreatedChart()
        {}

        RecentlyCreatedChart(String location, String filterTitle, String filterUrl, String imageMap, String imageMapName, Integer issueCount,Integer width, Integer height, DataRow[] data)
        {
            this.location = location;
            this.filterTitle = filterTitle;
            this.filterUrl = filterUrl;
            this.imageMap = imageMap;
            this.imageMapName = imageMapName;
            this.issueCount = issueCount;
            this.height = height;
            this.width = width;
            this.data = data;
        }

        public String getLocation()
        {
            return location;
        }

        public String getFilterTitle()
        {
            return filterTitle;
        }

        public String getFilterUrl()
        {
            return filterUrl;
        }

        public String getImageMap()
        {
            return imageMap;
        }

        public String getImageMapName()
        {
            return imageMapName;
        }

        public Integer getIssueCount()
        {
            return issueCount;
        }

        public Integer getHeight()
        {
            return height;
        }

        public Integer getWidth()
        {
            return width;
        }

        public DataRow[] getData()
        {
            return data;
        }

        @Override
        public int hashCode()
        {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public boolean equals(final Object o)
        {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }
    }

    @XmlRootElement
    //have to define a namespace here, since there's other 'DataRow' JAXB beans
    @XmlType (namespace = "com.atlassian.jira.gadgets.system.RecentlyCreatedChartResource")
    public static class DataRow
    {
        private Object key;

        @XmlElement
        private int createdValue;

        @XmlElement
        private int resolvedValue;

        @XmlElement
        private String resolvedUrl;

        @XmlElement
        private int unresolvedValue;

        @XmlElement
        private String unresolvedUrl;

        @XmlElement(name="key")
        private String keyString;

        @SuppressWarnings ({ "UnusedDeclaration", "unused" })
        public DataRow()
        {}

        public DataRow(final Object key, final int createdValue, final int resolvedValue, final String resolvedUrl, final int unresolvedValue, final String unresolvedUrl)
        {
            this.key = key;
            this.createdValue = createdValue;
            this.resolvedValue = resolvedValue;
            this.resolvedUrl = resolvedUrl;
            this.unresolvedValue = unresolvedValue;
            this.unresolvedUrl = unresolvedUrl;
            this.keyString = key.toString();
        }

        public String getKey()
        {
            return key.toString();
        }

        public Object getRawKey()
        {
            return key;
        }

        public int getCreatedValue()
        {
            return createdValue;
        }

        public int getResolvedValue()
        {
            return resolvedValue;
        }

        public String getResolvedUrl()
        {
            return resolvedUrl;
        }

        public int getUnresolvedValue()
        {
            return unresolvedValue;
        }

        public String getUnresolvedUrl()
        {
            return unresolvedUrl;
        }

        @Override
        public int hashCode()
        {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public boolean equals(final Object o)
        {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }
    }
    ///CLOVER:ON
}
