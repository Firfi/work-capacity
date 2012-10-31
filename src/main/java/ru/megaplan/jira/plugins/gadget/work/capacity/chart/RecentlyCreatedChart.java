package ru.megaplan.jira.plugins.gadget.work.capacity.chart;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.charts.Chart;
import com.atlassian.jira.charts.ChartFactory;
import com.atlassian.jira.charts.jfreechart.ChartHelper;
import com.atlassian.jira.charts.jfreechart.TimePeriodUtils;
import com.atlassian.jira.charts.jfreechart.util.ChartUtil;
import com.atlassian.jira.charts.util.DataUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.dbc.Assertions;
import com.atlassian.jira.util.velocity.DefaultVelocityRequestContextFactory;
import com.atlassian.jira.util.velocity.VelocityRequestContext;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.query.Query;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.RegularTimePeriod;
import ru.megaplan.jira.plugins.gadget.work.capacity.action.util.StatusFact;
import ru.megaplan.jira.plugins.gadget.work.capacity.service.CapacityHistoryService;

import java.io.IOException;
import java.util.*;

/**
 * Produces a chart that displays the average age issues have been open for for a certain period.
 *
 * @since v4.0
 */
public class RecentlyCreatedChart
{

    private final static Logger log = Logger.getLogger(RecentlyCreatedChart.class);

    private final SearchProvider searchProvider;
    private final IssueIndexManager issueIndexManager;
    private final SearchService searchService;
    private final ApplicationProperties applicationProperties;
    private final TimeZoneManager timeZoneManager;
    private final CapacityHistoryService capacityHistoryService;

    public RecentlyCreatedChart(SearchProvider searchProvider, IssueIndexManager issueIndexManager,
                                SearchService searchService, ApplicationProperties applicationProperties, TimeZoneManager timeZoneManager, CapacityHistoryService capacityHistoryService)
    {
        this.searchProvider = searchProvider;
        this.issueIndexManager = issueIndexManager;
        this.searchService = searchService;
        this.applicationProperties = applicationProperties;
        this.timeZoneManager = timeZoneManager;
        this.capacityHistoryService = capacityHistoryService;
    }

    public Chart generateChart(final User remoteUser, final SearchRequest searchRequest, int daysBefore, int daysAfter, final ChartFactory.PeriodName periodName, boolean showUnresolvedTrend, int width, int height, Set<String> statusesIn, Set<String> assignees, boolean mergeStatuses, boolean countOldFacts)
    {
        Assertions.notNull("searchRequest", searchRequest);

        try
        {
            final Query query = searchRequest.getQuery();
            final TimeZone userTimeZone = getTimeZone();
            boolean makeResolvedNegative = true;
            Class timePeriodClass = ChartUtil.getTimePeriodClass(periodName);
            //final JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder(searchRequest.getQuery());
            //final JqlClauseBuilder whereClauseBuilder = queryBuilder.where().defaultAnd();
            //whereClauseBuilder.createdAfter("-" + days + "d");
            List<StatusFact> facts = capacityHistoryService.getStatusFacts(query, remoteUser);
            final Map<String,Map<RegularTimePeriod, Number>> createdUnresolvedSum = CapacityChart.getChangedIssues(facts, periodName, true, daysBefore, daysAfter, statusesIn, assignees, mergeStatuses, userTimeZone, false, null);
            final Map<String,Map<RegularTimePeriod, Number>>  createdResolvedSum = CapacityChart.getChangedIssues(facts, periodName, false, daysBefore, daysAfter, statusesIn, assignees, mergeStatuses, userTimeZone, false, null);
            normalizeData(createdResolvedSum, createdUnresolvedSum, daysBefore, daysAfter, timePeriodClass, userTimeZone, makeResolvedNegative);

            final Map<String, Map<RegularTimePeriod, Number>> resolvedDataMapByAssignee = CapacityChart.getChangedIssues(facts, periodName, true, daysBefore, daysAfter, statusesIn, assignees, mergeStatuses, getTimeZone(), true, null);
            final Map<String, Map<RegularTimePeriod, Number>> сreatedDataMapByAssignee = CapacityChart.getChangedIssues(facts, periodName, false, daysBefore,daysAfter, statusesIn, assignees, mergeStatuses, getTimeZone(), true, null);
            normalizeData(сreatedDataMapByAssignee, resolvedDataMapByAssignee, daysBefore, daysAfter, timePeriodClass, userTimeZone, makeResolvedNegative);


            //Collector hitCollector = new ResolutionSplittingCreatedIssuesHitCollector(createdResolved, createdUnresolved, issueIndexManager.getIssueSearcher(), timePeriodClass, userTimeZone);
            //searchProvider.search(query, remoteUser, hitCollector);




            Map<RegularTimePeriod, Number> createdResolved = createdResolvedSum.get(CapacityChart.SUM_KEY);
            Map<RegularTimePeriod, Number> createdUnresolved = createdUnresolvedSum.get(CapacityChart.SUM_KEY);

            final I18nBean i18nBean = new I18nBean(remoteUser);
            final Series createdUnresolvedSeries = new Series("IN", createdUnresolved);
            final Series createdResolvedSeries = new Series("OUT", createdResolved);
            Series trendSeries = null;

            final Map<RegularTimePeriod, Number> unresolvedTrendDataMap = new TreeMap<RegularTimePeriod, Number>();
            if (showUnresolvedTrend)
            {
                float unresolvedTrend = 0;
                for ( RegularTimePeriod key : createdUnresolved.keySet())
                {
                    Number created = createdUnresolved.get(key);
                    Number resolved = createdResolved.get(key);

                    unresolvedTrend = Math.abs(created.intValue()!=0?(float)resolved.intValue()/created.intValue():resolved.intValue()!=0?1:0)*100;
                    unresolvedTrendDataMap.put(key, unresolvedTrend);
                }
                trendSeries = new Series("Trend", unresolvedTrendDataMap);
            }
            CategoryDataset dataset = DataUtils.getCategoryDataset(
                    Lists.newArrayList(createdUnresolvedSeries.data,createdResolvedSeries.data),
                    new String[] { createdUnresolvedSeries.name,createdResolvedSeries.name}
            );
            CategoryDataset assigneeDetailsDatasetCreated = getAssigneeDetailsDataset(сreatedDataMapByAssignee);
            CategoryDataset assigneeDetailsDatasetResolved = getAssigneeDetailsDataset(resolvedDataMapByAssignee);

            final ChartHelper helper;
            CategoryDataset trendDataset = null;
            if (trendSeries != null) {
                trendDataset = DataUtils.getCategoryDataset(Lists.newArrayList(trendSeries.data), new String[] {trendSeries.name});
            }

            helper = new ru.megaplan.jira.plugins.gadget.work.capacity.chart.generator.StackedBarChartGenerator(dataset, trendDataset, assigneeDetailsDatasetCreated, assigneeDetailsDatasetResolved , i18nBean).generateChart();

            JFreeChart chart = helper.getChart();
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            CategoryItemRenderer renderer =  plot.getRenderer();
            renderer.setToolTipGenerator(new CategoryToolTipGenerator()
            {
                public String generateToolTip(CategoryDataset categoryDataset, int row, int col)
                {
                    String periodAsString = (String) categoryDataset.getColumnKey(col);
                    int resolved = createdResolvedSeries.getValue(periodAsString).intValue();
                    int unresolved = createdUnresolvedSeries.getValue(periodAsString).intValue();
                    int total = resolved + unresolved;
                    if (row == 0)
                    {
                        return periodAsString + ": " + unresolved + " / " + total + " " + "IN" + ".";
                    }
                    else if (row == 1)
                    {
                        return periodAsString + ": " + resolved + " / " + total + " " + "OUT" + ".";
                    }
                    return "";
                }
            });
            final VelocityRequestContext velocityRequestContext = new DefaultVelocityRequestContextFactory(applicationProperties).getJiraVelocityRequestContext();
//            CategoryURLGenerator urlGenerator = new CategoryURLGenerator()
//            {
//                public String generateURL(CategoryDataset categoryDataset, int row, int col)
//                {
//                    String periodAsString = (String) categoryDataset.getColumnKey(col);
//                    StatisticsMapper createdMapper = new DatePeriodStatisticsMapper(ChartUtil.getTimePeriodClass(periodName), DocumentConstants.ISSUE_CREATED, userTimeZone);
//                    SearchRequest searchUrlSuffix = createdMapper.getSearchUrlSuffix(createdResolvedSeries.getTimePeriod(periodAsString), searchRequest);
//                    Query query;
//                    if (row == 0)
//                    {
//                        JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder(searchUrlSuffix.getQuery());
//                       // queryBuilder.where().and().unresolved();
//                        query = queryBuilder.buildQuery();
//                    }
//                    else if (row == 1)
//                    {
//                        JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder(searchUrlSuffix.getQuery());
//                       // queryBuilder.where().and().not().unresolved();
//                        query = queryBuilder.buildQuery();
//                    }
//                    else
//                    {
//                        query = searchUrlSuffix == null ? new QueryImpl() : searchUrlSuffix.getQuery();
//                    }
//
//                    QueryOptimizer optimizer = new RedundantClausesQueryOptimizer();
//                    query = optimizer.optimizeQuery(query);
//
//                    return velocityRequestContext.getCanonicalBaseUrl() + "/secure/IssueNavigator.jspa?reset=true" + searchService.getQueryString(remoteUser, query);
//
//                }
//            };
           // renderer.setItemURLGenerator(urlGenerator);
  //          plot.setRenderer(renderer);
            helper.generate(width, height);
            final Map<String, Object> params = new HashMap<String, Object>();
            params.put("chart", helper.getLocation());
            params.put("chartDataset", dataset);
            params.put("completeDataset", dataset);
           // params.put("completeDatasetUrlGenerator", urlGenerator);
            params.put("numIssues", DataUtils.getTotalNumber(createdResolved) + DataUtils.getTotalNumber(createdUnresolved));
            params.put("period", periodName.toString());
            params.put("imagemap", helper.getImageMap());
            params.put("imagemapName", helper.getImageMapName());
            params.put("daysPrevious", daysBefore);
            params.put("width", width);
            params.put("height", height);

            return new Chart(helper.getLocation(), helper.getImageMap(), helper.getImageMapName(), params);
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

    private CategoryDataset getAssigneeDetailsDataset(Map<String, Map<RegularTimePeriod, Number>> mapByAssignee) {
        List<Map<String, Number>> data = new ArrayList<Map<String, Number>>();
        List<String> names = new ArrayList<String>();
        UserManager userManager = ComponentAccessor.getUserManager();
        for (Map.Entry<String, Map<RegularTimePeriod, Number>> e : mapByAssignee.entrySet()) {
            User user = userManager.getUser(e.getKey());
            String username;
            if (user == null) {
                username = e.getKey();
            } else {
                username = user.getDisplayName();
            }
            Series series = new Series(username, e.getValue());
            data.add(series.data);
            names.add(series.name);

        }
        CategoryDataset dataset = DataUtils.getCategoryDataset(
                data,
                names.toArray(new String[names.size()])
        );
        return dataset;
    }

    public static Map<RegularTimePeriod, Number> _mergeAssignees(Map<String, Map<RegularTimePeriod, Number>> mapByAssignee) {
        Map<RegularTimePeriod, MutableDouble> result = new TreeMap<RegularTimePeriod, org.apache.commons.lang3.mutable.MutableDouble>();
        for (Map<RegularTimePeriod, Number> map : mapByAssignee.values()) {
            for (Map.Entry<RegularTimePeriod, Number> e : map.entrySet()) {
                MutableDouble numberOfFactsSum = result.get(e.getKey());
                if (numberOfFactsSum == null) {
                    result.put(e.getKey(), new MutableDouble());
                }
                result.get(e.getKey()).add(e.getValue());
            }
        }
        Map<RegularTimePeriod, Number> nonMutableResult = new TreeMap<RegularTimePeriod, java.lang.Number>();
        for (Map.Entry<RegularTimePeriod, MutableDouble> e : result.entrySet()) {
            nonMutableResult.put(e.getKey(), e.getValue().getValue());
        }

        return nonMutableResult;
    }

    public static void normalizeData(Map<String, Map<RegularTimePeriod, Number>> createdResolvedByAssignee, Map<String, Map<RegularTimePeriod, Number>> createdUnresolvedByAssignee, int daysBefore, int daysAfter, Class timePeriodClass, TimeZone userTimeZone, boolean makeResolvedNegative) {
        for (String assignee : createdResolvedByAssignee.keySet()) {
            if (createdUnresolvedByAssignee.get(assignee) == null) {
                createdUnresolvedByAssignee.put(assignee, new TreeMap<RegularTimePeriod, Number>());
            }
        }
        for (String assignee : createdUnresolvedByAssignee.keySet()) {
            if (createdResolvedByAssignee.get(assignee) == null) {
                createdResolvedByAssignee.put(assignee, new TreeMap<RegularTimePeriod, Number>());
            }
        }
        for (Map.Entry<String,Map<RegularTimePeriod, Number>> forAssignee : createdResolvedByAssignee.entrySet()) {
            normaliseDateRangeCount(forAssignee.getValue(), daysBefore - 1, daysAfter, timePeriodClass, userTimeZone);   // only need to do one map as normalising keys will fix second
            DataUtils.normaliseMapKeys(forAssignee.getValue(), createdUnresolvedByAssignee.get(forAssignee.getKey()));
            if (makeResolvedNegative) makeNegative(forAssignee.getValue());
        }
    }

    public static void makeNegative(Map<RegularTimePeriod, Number> createdResolved) {
        for (Map.Entry<RegularTimePeriod, Number> e : createdResolved.entrySet()) {
            e.setValue(-e.getValue().intValue());
        }
    }

    public static void normaliseDateRangeCount(Map<RegularTimePeriod, Number> dateMap, int daysBefore, int daysAfter, Class period, TimeZone timeZone)
    {
        // find earliest date, then move it forwards until we hit now
        Calendar calBefore = Calendar.getInstance(timeZone);
        calBefore.add(Calendar.DAY_OF_MONTH, - daysBefore);
        Date earliest = calBefore.getTime();
        Calendar calAfter = Calendar.getInstance(timeZone);
        calAfter.add(Calendar.DAY_OF_MONTH, - daysAfter);
        Date latest = calAfter.getTime();
        RegularTimePeriod cursor = RegularTimePeriod.createInstance(period, earliest, timeZone);
        RegularTimePeriod end = RegularTimePeriod.createInstance(period, latest, timeZone);
        //RegularTimePeriod end = RegularTimePeriod.createInstance(period, new Date(), timeZone);

        //fix for JRA-11686.  Prevents the loop from looping infinitely.
        while (cursor != null && cursor.compareTo(end) <= 0)
        {
            if (!dateMap.containsKey(cursor))
            {
                dateMap.put(cursor, 0);
            }
            cursor = cursor.next();
            cursor.peg(calBefore);
            cursor.peg(calAfter);
        }
    }


    public class Series
    {
        final Map<String, Number> data;
        final Map<String, RegularTimePeriod> columnKeyToTimePeriod;
        final String name;

        Series(String name, Map<RegularTimePeriod, Number> data)
        {
            this.name = name;
            this.data = convertDomainAxisValues(data);
            this.columnKeyToTimePeriod = mapByAxisValue(data);
        }

        ImmutableMap<String, Number> convertDomainAxisValues(Map<RegularTimePeriod, Number> data)
        {
            ImmutableMap.Builder<String, Number> result = ImmutableMap.builder();
            for (Map.Entry<RegularTimePeriod, Number> entry : data.entrySet())
            {
                result.put(new TimePeriodUtils(timeZoneManager).prettyPrint(entry.getKey()), entry.getValue());
            }

            return result.build();
        }

        ImmutableMap<String, RegularTimePeriod> mapByAxisValue(Map<RegularTimePeriod, Number> data)
        {
            ImmutableMap.Builder<String, RegularTimePeriod> result = ImmutableMap.builder();
            for (Map.Entry<RegularTimePeriod, Number> entry : data.entrySet())
            {
                result.put(new TimePeriodUtils(timeZoneManager).prettyPrint(entry.getKey()), entry.getKey());
            }

            return result.build();
        }

        public RegularTimePeriod getTimePeriod(String columnKey)
        {
            return columnKeyToTimePeriod.get(columnKey);
        }

        public Number getValue(String columnKey)
        {
            return data.get(columnKey);
        }
    }
    public static <T extends Comparable> CategoryDataset getCategoryDataset(List<Map<T, Number>> dataMaps, String[] seriesNames)
    {
        final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (dataMaps.size() != seriesNames.length)
        {
            throw new IllegalArgumentException("Number of datamaps and series names must be equal.");
        }

        for (int i = 0; i < seriesNames.length; i++)
        {
            final String seriesName = seriesNames[i];
            final Map<T, Number> data = dataMaps.get(i);

            for (final Map.Entry<T, Number> entry : data.entrySet())
            {
                dataset.addValue(entry.getValue(), seriesName, entry.getKey());
            }
        }

        return dataset;
    }


    private TimeZone getTimeZone()
    {
        return timeZoneManager.getLoggedInUserTimeZone();
    }

}