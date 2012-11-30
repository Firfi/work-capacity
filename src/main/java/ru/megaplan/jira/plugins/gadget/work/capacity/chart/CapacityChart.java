
package ru.megaplan.jira.plugins.gadget.work.capacity.chart;

import com.atlassian.core.util.DateUtils;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.gzipfilter.org.apache.commons.lang.math.NumberUtils;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.charts.Chart;
import com.atlassian.jira.charts.ChartFactory;
import com.atlassian.jira.charts.jfreechart.ChartHelper;
import com.atlassian.jira.charts.jfreechart.CreatedVsResolvedChartGenerator;
import com.atlassian.jira.charts.jfreechart.util.ChartUtil;
import com.atlassian.jira.charts.util.DataUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.index.DocumentConstants;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.issue.search.SearchContext;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.search.managers.IssueSearcherManager;
import com.atlassian.jira.issue.search.searchers.IssueSearcher;
import com.atlassian.jira.issue.search.searchers.transformer.ProjectSearchInputTransformer;
import com.atlassian.jira.issue.statistics.DatePeriodStatisticsMapper;
import com.atlassian.jira.issue.statistics.StatisticsMapper;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.util.dbc.Assertions;
import com.atlassian.jira.util.velocity.DefaultVelocityRequestContextFactory;
import com.atlassian.jira.util.velocity.VelocityRequestContext;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.query.Query;
import com.atlassian.query.QueryImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.log4j.Logger;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.StatusFact;
import ru.megaplan.jira.plugins.gadget.work.capacity.resource.CreatedVsResolvedResource;
import ru.megaplan.jira.plugins.gadget.work.capacity.service.CapacityHistoryService;

import java.awt.*;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

import static com.atlassian.jira.charts.ChartFactory.PeriodName;
import static com.atlassian.jira.charts.ChartFactory.VersionLabel;

/**
 * Produces a created vs resolved chart given a searchrequest.
 *
 * @since v4.0
 */
public class CapacityChart
{

    private final static Logger log = Logger.getLogger(CapacityChart.class);

    private final SearchProvider searchProvider;
    private final VersionManager versionManager;
    private final IssueIndexManager issueIndexManager;
    private final IssueSearcherManager issueSearcherManager;
    private final ProjectManager projectManager;
    private final ApplicationProperties applicationProperties;
    private final SearchService searchService;
    private final TimeZoneManager timeZoneManager;
    private final CapacityHistoryService capacityHistoryService;


    public CapacityChart(SearchProvider searchProvider, VersionManager versionManager,
                         IssueIndexManager issueIndexManager, IssueSearcherManager issueSearcherManager,
                         ProjectManager projectManager, ApplicationProperties applicationProperties, SearchService searchService,
                         TimeZoneManager timeZoneManager, CapacityHistoryService capacityHistoryService)
    {
        this.searchProvider = searchProvider;
        this.versionManager = versionManager;
        this.issueIndexManager = issueIndexManager;
        this.issueSearcherManager = issueSearcherManager;
        this.projectManager = projectManager;
        this.applicationProperties = applicationProperties;
        this.searchService = searchService;
        this.timeZoneManager = timeZoneManager;
        this.capacityHistoryService = capacityHistoryService;
    }

    public Chart generateChart(final User remoteUser, final SearchRequest searchRequest, int daysBefore, int daysAfter, final PeriodName periodName, VersionLabel versionLabel, final boolean cumulative,
                               boolean showUnresolvedTrend, int width, int height, Set<String> statusesIn, Set<String> assignees, boolean mergeStatuses, boolean countOldFacts)
    {
        Assertions.notNull("searchrequest", searchRequest);
        int maxdays = 1000;
        daysBefore = normalizeDaysValue(daysBefore, periodName, 1, maxdays); //first is mindays
        daysAfter = normalizeDaysValue(daysAfter, periodName, 0, maxdays);
        try
        {
            final Map<String, Object> params = new HashMap<String, Object>();

            final Query query = searchRequest.getQuery();
            final SearchContext searchContext = searchService.getSearchContext(remoteUser, query);
            List domainMarkers = null;
            final IssueSearcher projectSearcher = issueSearcherManager.getSearcher(IssueFieldConstants.PROJECT);
            final ProjectSearchInputTransformer searchInputTransformer = (ProjectSearchInputTransformer) projectSearcher.getSearchInputTransformer();
            final Set<String> projectIds = searchInputTransformer.getIdValuesAsStrings(remoteUser, query, searchContext);
            if (projectIds != null && !VersionLabel.none.equals(versionLabel))
            {
                domainMarkers = getDomainMarkers(projectIds, daysBefore, periodName, versionLabel);
            }

            //JqlQueryBuilder jqlQueryBuilder = JqlQueryBuilder.newBuilder(query);
            //JqlClauseBuilder whereClauseBuilder = jqlQueryBuilder.where().defaultAnd();
            List<StatusFact> facts = capacityHistoryService.getStatusFacts(query, remoteUser);

            final Class timePeriodClass = ChartUtil.getTimePeriodClass(periodName);
            boolean makeResolvedNegative = false;

            Map<String, MutableDouble> oldFactsSumForAssigneeCreated = new HashMap<String, MutableDouble>();
            Map<String, MutableDouble> oldFactsSumForAssigneeResolved = new HashMap<String, MutableDouble>();

            final Map<String, Map<RegularTimePeriod, Number>> сreatedDataMapByAssignee = getChangedIssues(facts, periodName, true, daysBefore, daysAfter, statusesIn, assignees, mergeStatuses, getTimeZone(), true, oldFactsSumForAssigneeCreated);
            final Map<String, Map<RegularTimePeriod, Number>> resolvedDataMapByAssignee = getChangedIssues(facts, periodName, false, daysBefore,daysAfter, statusesIn, assignees, mergeStatuses, getTimeZone(), true, oldFactsSumForAssigneeResolved);

            Map<String, MutableDouble> oldFactsSumForAssigneeCreatedSum = new HashMap<String, MutableDouble>();
            Map<String, MutableDouble> oldFactsSumForAssigneeResolvedSum = new HashMap<String, MutableDouble>();

            final Map<String, Map<RegularTimePeriod, Number>> createdDataMapSum =
                    getChangedIssues(facts, periodName, true, daysBefore, daysAfter, statusesIn, assignees, mergeStatuses, getTimeZone(), false, oldFactsSumForAssigneeCreatedSum);
            final Map<String, Map<RegularTimePeriod, Number>> resolvedDataMapSum =
                    getChangedIssues(facts, periodName, false, daysBefore, daysAfter, statusesIn, assignees, mergeStatuses, getTimeZone(), false, oldFactsSumForAssigneeResolvedSum);

            RecentlyCreatedChart.normalizeData(createdDataMapSum, resolvedDataMapSum, daysBefore - 1, daysAfter, timePeriodClass, timeZoneManager.getLoggedInUserTimeZone(), makeResolvedNegative);
            RecentlyCreatedChart.normalizeData(сreatedDataMapByAssignee, resolvedDataMapByAssignee, daysBefore - 1, daysAfter, timePeriodClass, timeZoneManager.getLoggedInUserTimeZone(), makeResolvedNegative);

            final Map<RegularTimePeriod, Number> createdDataMap = createdDataMapSum.get(SUM_KEY);
            final Map<RegularTimePeriod, Number> resolvedDataMap = resolvedDataMapSum.get(SUM_KEY);

            params.put(CreatedVsResolvedResource.NUM_CREATED_ISSUES, DataUtils.getTotalNumber(createdDataMap));
            params.put(CreatedVsResolvedResource.NUM_RESOLVED_ISSUES, DataUtils.getTotalNumber(resolvedDataMap));

            final Number oldFactsSumCreated = oldFactsSumForAssigneeCreatedSum.get(SUM_KEY);
            final Number oldFactsSumResolved = oldFactsSumForAssigneeResolvedSum.get(SUM_KEY);

            if (cumulative)
            {
                DataUtils.makeCumulative(createdDataMap);
                DataUtils.makeCumulative(resolvedDataMap);
            }
            if (countOldFacts) {
                for (Map.Entry<RegularTimePeriod, Number> e : createdDataMap.entrySet()) {
                    e.setValue(e.getValue().doubleValue()+oldFactsSumCreated.doubleValue());
                }
                for (Map.Entry<RegularTimePeriod, Number> e : resolvedDataMap.entrySet()) {
                    e.setValue(e.getValue().doubleValue()+oldFactsSumResolved.doubleValue());
                }
            }




            // calculate trend of unresolved as a difference of the existing maps
            final Map<RegularTimePeriod, Number> unresolvedTrendDataMap = new TreeMap<RegularTimePeriod, Number>();
            if (showUnresolvedTrend)
            {
                int unresolvedTrend = 0;
                for (RegularTimePeriod key : createdDataMap.keySet())
                {
                    Number created = createdDataMap.get(key);
                    Number resolved = resolvedDataMap.get(key);

                    unresolvedTrend = unresolvedTrend + created.intValue() - resolved.intValue();
                    unresolvedTrendDataMap.put(key, unresolvedTrend);
                }
            }

            final I18nBean i18nBean = getI18nBean(remoteUser);
            String created = i18nBean.getText("issue.field.created");
            String resolved = i18nBean.getText("portlet.createdvsresolved.resolved");
            String unresolvedTrend = i18nBean.getText("portlet.createdvsresolved.trendOfUnresolved");
            Map[] dataMaps = showUnresolvedTrend ? new Map[] { createdDataMap, resolvedDataMap, unresolvedTrendDataMap } : new Map[] { createdDataMap, resolvedDataMap };
            String[] seriesNames = showUnresolvedTrend ? new String[] { created, resolved, unresolvedTrend } : new String[] { created, resolved };
            CategoryDataset dataset = getCategoryDataset(dataMaps, seriesNames);



            XYDataset createdVsResolved = generateTimeSeriesXYDataset(created, createdDataMap, resolved, resolvedDataMap);
            TimeSeries trendSeries = null;
            if (showUnresolvedTrend)
            {
                trendSeries = createTimeSeries(unresolvedTrend, unresolvedTrendDataMap);
            }
            ChartHelper chartHelper = new CreatedVsResolvedChartGenerator(createdVsResolved, trendSeries, domainMarkers, i18nBean).generateChart();

            XYPlot plot = (XYPlot) chartHelper.getChart().getPlot();
            XYItemRenderer renderer = plot.getRenderer();
            renderer.setToolTipGenerator(new StandardXYToolTipGenerator("{0} {2} " + i18nBean.getText("portlet.createdvsresolved.tooltip.issues"), NumberFormat.getNumberInstance(), NumberFormat.getNumberInstance()));
            final VelocityRequestContext velocityRequestContext = new DefaultVelocityRequestContextFactory(applicationProperties).getJiraVelocityRequestContext();
            XYURLGenerator xyurlGenerator = new XYURLGenerator()
            {
                public String generateURL(XYDataset xyDataset, int series, int item)
                {
                    final TimeSeriesCollection timeSeriesCollection = (TimeSeriesCollection) xyDataset;
                    //only display links if the chart is not cumulative.  If it's cumulative, links don't make sense!
                    if (!cumulative && series < timeSeriesCollection.getSeriesCount())
                    {
                        final TimeSeries timeSeries = timeSeriesCollection.getSeries(series);
                        final RegularTimePeriod timePeriod = timeSeries.getTimePeriod(item);
                        StatisticsMapper mapper = null;
                        if (series == 0)
                        {
                            mapper = new DatePeriodStatisticsMapper(ChartUtil.getTimePeriodClass(periodName), DocumentConstants.ISSUE_CREATED, getTimeZone());
                        }
                        else if (series == 1)
                        {
                            mapper = new DatePeriodStatisticsMapper(ChartUtil.getTimePeriodClass(periodName), DocumentConstants.ISSUE_RESOLUTION_DATE, getTimeZone());
                        }
                        if (mapper != null)
                        {
                            final SearchRequest searchUrlSuffix = mapper.getSearchUrlSuffix(timePeriod, searchRequest);
                            String url = velocityRequestContext.getCanonicalBaseUrl() + "/secure/IssueNavigator.jspa?reset=true" + searchService.getQueryString(remoteUser, (searchUrlSuffix == null) ? new QueryImpl() : searchUrlSuffix.getQuery());
                            return url;
                        }
                    }
                    return null;
                }
            };
            renderer.setURLGenerator(xyurlGenerator);
//
//
            chartHelper.generate(width, height);

            params.put("chart", chartHelper.getLocation());
            params.put("daysPrevious", daysBefore);
            params.put("daysAfret", daysAfter);
            params.put("chartDataset", createdVsResolved);
            params.put("trendSeries", trendSeries);
            params.put("completeDataset", dataset);
            params.put("completeDatasetUrlGenerator", xyurlGenerator);
            params.put("period", periodName.toString());
            params.put("cumulative", Boolean.toString(cumulative));
            params.put("showUnresolvedTrend", Boolean.toString(showUnresolvedTrend));
            params.put("versionLabels", versionLabel.toString());
            params.put("imagemap", chartHelper.getImageMap());
            params.put("imagemapName", chartHelper.getImageMapName());
            params.put("imageWidth", width);
            params.put("imageHeight", height);

            return new Chart(chartHelper.getLocation(), chartHelper.getImageMap(), chartHelper.getImageMapName(), params);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error generating chart", e);
        }
        catch (SearchException e)
        {
            throw new RuntimeException("Error generating chart", e);
        }
    }

    public static int normalizeDaysValue(int days, ChartFactory.PeriodName period, int mindays, int maxdays)
    {
        final ApplicationProperties applicationProperties = ComponentAccessor.getComponent(ApplicationProperties.class);
        final String limitString = applicationProperties.getDefaultBackedString(APKeys.JIRA_CHART_DAYS_PREVIOUS_LIMIT_PREFIX + period.toString());
        if(StringUtils.isNotEmpty(limitString))
        {
            int limit = NumberUtils.toInt(limitString, maxdays);
            return Math.max(Math.min(days, limit), mindays);
        }

        return Math.max(Math.min(days, maxdays), mindays);
    }





    private TimeZone getTimeZone()
    {
        return timeZoneManager.getLoggedInUserTimeZone();
    }

    private List getDomainMarkers(Set<String> projectIds, int days, ChartFactory.PeriodName periodName, VersionLabel versionLabel)
    {
        //if we don't want versions labels, or if we don't have any project ids don't show labels.
        if (VersionLabel.none.equals(versionLabel) || projectIds.isEmpty())
        {
            return Collections.EMPTY_LIST;
        }

        final List<Long> searchedProjectIds = transformToLongs(projectIds);
        final Set<Version> versions = new HashSet<Version>();
        final Map<Long, String> projectIdToNameMapping = new HashMap<Long, String>();
        for (Long searchedProjectId : searchedProjectIds)
        {
            // JRA-18210 - we should only included unarchived versions
            versions.addAll(versionManager.getVersionsUnarchived(searchedProjectId));
            //if there's more than one project we'll want to display the project key next to the version
            //so the user knows what project a particular version corresponds to.
            if (searchedProjectIds.size() > 1)
            {
                final Project projectObj = projectManager.getProjectObj(searchedProjectId);
                if (projectObj != null)
                {
                    projectIdToNameMapping.put(searchedProjectId, projectObj.getKey());
                }
            }
        }

        final Date releasedAfter = new Date(System.currentTimeMillis() - days * DateUtils.DAY_MILLIS);
        final List<ValueMarker> markers = new ArrayList<ValueMarker>();
        final Class periodClass = ChartUtil.getTimePeriodClass(periodName);

        for (final Version version : versions)
        {
            if (version.getReleaseDate() != null && releasedAfter.before(version.getReleaseDate()))
            {
                RegularTimePeriod timePeriod = RegularTimePeriod.createInstance(periodClass, version.getReleaseDate(), RegularTimePeriod.DEFAULT_TIME_ZONE);
                ValueMarker valueMarker = new ValueMarker(timePeriod.getFirstMillisecond());
                boolean isMinorVersion = isMinorVersion(version);

                // skip minor versions
                if (VersionLabel.major.equals(versionLabel) && isMinorVersion)
                {
                    continue;
                }

                if (isMinorVersion)
                {
                    valueMarker.setPaint(Color.LIGHT_GRAY); // minor version
                    valueMarker.setStroke(new BasicStroke(1.0f));
                }
                else
                {
                    valueMarker.setPaint(Color.GRAY); // major version
                    valueMarker.setStroke(new BasicStroke(1.2f));
                    valueMarker.setLabelPaint(Color.GRAY);
                    String valueMarkerLabel = version.getName();
                    final Long projectId = version.getProjectObject().getId();
                    //if there's a mapping, use it.
                    if (projectIdToNameMapping.containsKey(projectId))
                    {
                        valueMarkerLabel = valueMarkerLabel + "[" + projectIdToNameMapping.get(projectId) + "]";
                    }
                    valueMarker.setLabel(valueMarkerLabel);
                }
                markers.add(valueMarker);
            }
        }

        return markers;
    }

    private List<Long> transformToLongs(final Set<String> projects)
    {
        final List<Long> ids = new ArrayList<Long>(projects.size());

        for (String idStr : projects)
        {
            final Long id = getValueAsLong(idStr);
            if (id != null)
            {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long getValueAsLong(final String value)
    {
        try
        {
            return new Long(value);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private CategoryDataset getCategoryDataset(Map[] dataMaps, String[] seriesNames)
    {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        if (dataMaps.length != seriesNames.length)
        {
            throw new IllegalArgumentException("Number of datamaps and series names must be the equal.");
        }

        for (int i = 0; i < seriesNames.length; i++)
        {
            String seriesName = seriesNames[i];
            Map data = dataMaps[i];
            for (Iterator iterator = data.keySet().iterator(); iterator.hasNext(); )
            {
                RegularTimePeriod period = (RegularTimePeriod) iterator.next();
                dataset.addValue((Number) data.get(period), seriesName, period);
            }
        }

        return dataset;
    }

    public static XYDataset generateTimeSeriesXYDataset(String series1Name, Map series1Map, String series2Name, Map series2Map)
    {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        if (series1Name != null && series1Map != null)
        {
            dataset.addSeries(createTimeSeries(series1Name, series1Map));
        }
        if (series2Name != null && series2Map != null)
        {
            dataset.addSeries(createTimeSeries(series2Name, series2Map));
        }

        return dataset;
    }

    public static TimeSeries createTimeSeries(final String seriesName, final Map seriesMap)
    {
        TimeSeries series = null;

        for (Iterator iterator = seriesMap.keySet().iterator(); iterator.hasNext(); )
        {
            RegularTimePeriod period = (RegularTimePeriod) iterator.next();

            if (series == null)
            {
                series = new TimeSeries(seriesName, period.getClass());
            }

            series.add(period, (Number) seriesMap.get(period));
        }
        return series;
    }

    public final static String SUM_KEY = "_SUM_";

    public static Map<String,Map<RegularTimePeriod, Number>> getChangedIssues(List<StatusFact> facts,
                                                                              final PeriodName periodName,
                                                                              boolean to,
                                                                              int daysBefore,
                                                                              int daysAfter,
                                                                              Set<String> filterStatuses,
                                                                              Set<String> filterAssignee,
                                                                              boolean mergeStatuses,
                                                                              TimeZone timeZone,
                                                                              boolean divideByAssignees,
                                                                              Map<String, MutableDouble> oldFactsSumForAssignee
                                                                              ) throws IOException, SearchException {

        final Map<String, TreeMap<RegularTimePeriod, MutableDouble>> presult = new TreeMap<String, TreeMap<RegularTimePeriod, MutableDouble>>();
        Class timePeriodClass = ChartUtil.getTimePeriodClass(periodName);
        boolean countOldFacts = oldFactsSumForAssignee != null;
        Map<String, List<StatusFact>> factsByAssignee;
        if (divideByAssignees) {
            factsByAssignee = getFactsByAssignee(facts, filterAssignee);
        } else {
            factsByAssignee = new HashMap<String, List<StatusFact>>();
            factsByAssignee.put(SUM_KEY, facts);
        }

        for (Map.Entry<String, List<StatusFact>> factsForAssignee : factsByAssignee.entrySet()) {
            String assignee = factsForAssignee.getKey();
            MutableDouble oldFactsSum = new MutableDouble(0);
            if (countOldFacts) oldFactsSumForAssignee.put(assignee, oldFactsSum);
            TreeMap<RegularTimePeriod, MutableDouble> resultForAssignee =
                    getFactsSum(factsForAssignee.getValue(),
                            filterAssignee,
                            filterStatuses,
                            mergeStatuses,
                            to,
                            timePeriodClass,
                            timeZone,
                            daysBefore,
                            daysAfter,
                            oldFactsSum,
                            countOldFacts
                    );
            presult.put(assignee, resultForAssignee);
        }

        Map<String, Map<RegularTimePeriod, Number>> result = new HashMap<String, Map<RegularTimePeriod, Number>>();
        for (Map.Entry<String, ? extends Map<RegularTimePeriod, MutableDouble>> e : presult.entrySet()) {
            Map<RegularTimePeriod, Number> mapForAssignee = new TreeMap<RegularTimePeriod, Number>();
            for (Map.Entry<RegularTimePeriod, MutableDouble> originalForAssignee : e.getValue().entrySet()) {
                mapForAssignee.put(originalForAssignee.getKey(), originalForAssignee.getValue().doubleValue());
            }
            result.put(e.getKey(), mapForAssignee);
        }
        return result;
    }

    private static TreeMap<RegularTimePeriod, MutableDouble> getFactsSum(List<StatusFact> facts,
                                                                         Set<String> filterAssignee,
                                                                         Set<String> filterStatuses,
                                                                         boolean mergeStatuses,
                                                                         boolean to,
                                                                         Class timePeriodClass,
                                                                         TimeZone timeZone,
                                                                         int daysBefore,
                                                                         int daysAfter,
                                                                         MutableDouble oldFactsSum,
                                                                         boolean countOldFacts) {
        Calendar afterDay = Calendar.getInstance();
        afterDay.add(Calendar.DAY_OF_WEEK,-daysBefore);
        Calendar beforeDay = null;
        if (daysAfter != 0) {
            beforeDay = Calendar.getInstance();
            beforeDay.add(Calendar.DAY_OF_WEEK,-daysAfter);
        }
        TreeMap<RegularTimePeriod, MutableDouble> result = new TreeMap<RegularTimePeriod, MutableDouble>();
        StatusFact previousFact = null;
        for (StatusFact fact : facts) {
            if (filterFact(fact,filterAssignee,filterStatuses)) {
                continue;
            }

            Timestamp when;

            if (mergeStatuses) {
                if (previousFact == null) {
                    previousFact = new StatusFact(fact);
                    continue;
                } else {
                    if (to) {
                        if (previousFact.getWhen().getTime()+previousFact.getTime() == fact.getWhen().getTime() && previousFact.getPkey().equals(fact.getPkey())) {
                            previousFact.setTime(previousFact.getTime() + fact.getTime());
                            continue;
                        }
                    } else {
                        if (previousFact.getWhen().getTime()+previousFact.getTime() == fact.getWhen().getTime() && previousFact.getPkey().equals(fact.getPkey())) {
                            previousFact.setTime(previousFact.getTime() + fact.getTime());
                            if (fact.isActive()) {
                                previousFact = null; // just forgot that
                            }
                            continue;
                        }
                    }

                }
                when = getWhen(previousFact,to);
                if (!to && previousFact.isActive()) {
                    previousFact = new StatusFact(fact);
                    continue;
                }
                previousFact = new StatusFact(fact);
            } else {
                when = getWhen(fact,to);
            }

            incrementPresult(timePeriodClass, when, timeZone, result, afterDay, beforeDay, oldFactsSum, countOldFacts);

        }
        if (previousFact != null) {
            if (!filterFact(previousFact,filterAssignee, filterStatuses) && !(!to && previousFact.isActive())) {
                Timestamp when = getWhen(previousFact,to);
                incrementPresult(timePeriodClass, when, timeZone, result, afterDay, beforeDay, oldFactsSum, countOldFacts);
            }
        }

        return result;
    }

    private static void fillEmptyPeriods(TreeMap<RegularTimePeriod, Number> result, int daysBefore, int daysAfter, Class period, TimeZone timeZone) {
        RecentlyCreatedChart.normaliseDateRangeCount(result, daysBefore, daysAfter, period, timeZone);
    }

    private static Map<String, List<StatusFact>> getFactsByAssignee(List<StatusFact> facts, Set<String> filterAssignee) {
        Map<String, List<StatusFact>> result = new HashMap<String, List<StatusFact>>();
        for (StatusFact fact : facts) {
            if (!filterAssignee.contains(fact.getAssignee())) continue;
            List<StatusFact> factsForAssignee = result.get(fact.getAssignee());
            if (factsForAssignee == null) {
                factsForAssignee = new ArrayList<StatusFact>();
                result.put(fact.getAssignee(), factsForAssignee);
            }
            factsForAssignee.add(fact);
        }
        return result;
    }

    private static  boolean filterFact(StatusFact fact, Set<String> filterAssignee, Set<String> filterStatuses) {
        if (filterAssignee != null && !filterAssignee.isEmpty() && !filterAssignee.contains(fact.getAssignee())) {
            return true;
        }
        if (filterStatuses != null && !filterStatuses.isEmpty() && !filterStatuses.contains(fact.getStatus())) {
            return true;
        }
        return false;
    }

    private static void incrementPresult(Class timePeriodClass, Timestamp when, TimeZone timeZone, TreeMap<RegularTimePeriod, MutableDouble> presult, Calendar afterDay, Calendar beforeDay, MutableDouble oldFactsSum, boolean countOldFacts) {
        if ((afterDay != null && when.before(afterDay.getTime())) || (beforeDay != null && when.after(beforeDay.getTime()))) {
            if (countOldFacts) {
                oldFactsSum.increment();
            }
            return;
        }
        RegularTimePeriod factTime = RegularTimePeriod.createInstance(timePeriodClass, when, timeZone);
        MutableDouble num = presult.get(factTime);
        if (num == null) {
            num = new MutableDouble(0);
            presult.put(factTime, num);
        }
        num.increment();
    }

    private static  Timestamp getWhen(StatusFact previousFact, boolean to) {
        Timestamp when = null;
        if (to) {
            when = previousFact.getWhen();
        } else {
            when = previousFact.getWhen();
            when.setTime(when.getTime()+previousFact.getTime());
        }
        return when;
    }

    private I18nBean getI18nBean(User user)
    {
        return new I18nBean(user);
    }

    //Returns whether the version is major, based on some version naming conventions. (This is an egregious major/minor
    private boolean isMinorVersion(Version version)
    {
        return StringUtils.countMatches(version.getName(), ".") > 1 ||
                StringUtils.contains(version.getName().toLowerCase(), "alpha") ||
                StringUtils.contains(version.getName().toLowerCase(), "beta");
    }
}