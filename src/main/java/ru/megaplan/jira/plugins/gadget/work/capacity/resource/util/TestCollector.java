package ru.megaplan.jira.plugins.gadget.work.capacity.resource.util;

import com.atlassian.jira.issue.search.parameters.lucene.sort.JiraLuceneFieldFinder;
import com.atlassian.jira.issue.statistics.StatisticsMapper;
import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;

import java.io.IOException;
import java.util.*;

/**
 * A HitCollector that creates a doc -> object mapping.  This is useful for collecting documents where there are a
 * limited number of terms.  The caching also ensures that if multiple searches sort on the same terms, the doc ->
 * object mapping is maintained.
 * <p/>
 * This HitCollector can be quite memory intensive, however the cache is stored with a weak reference, so it will be
 * garbage collected.
 * <p/>
 * This HitCollector differs from {@link } in that it performs the term -> object
 * conversion here, rather than later.  This is more expensive, but useful for StatisticsMappers that perform some sort
 * of runtime conversion / translation (eg a StatisticsMapper that groups dates by Month, or groups users by email
 * domain name).
 */
public class TestCollector extends Collector
{

    private final static Logger log = Logger.getLogger(TestCollector.class);

    private StatisticsMapper statisticsMapper;
    private final Map<Object, Integer> result;
    private Collection<String>[] docToTerms;
    private int docBase = 0;
    private Long issueOriginalId;

    public TestCollector(StatisticsMapper statisticsMapper,
                         Map result, IndexReader historyIndexReader)
    {
        //noinspection unchecked
        this.result = result;
        this.statisticsMapper = statisticsMapper;
        try
        {
            docToTerms = JiraLuceneFieldFinder.getInstance().getMatches(historyIndexReader, statisticsMapper.getDocumentConstant());
        }
        catch (IOException e)
        {
            //ignore
        }
    }

    public void collect(int i)
    {
        //log.warn("collect in outer : " + docToTerms[docBase + i]);
        adjustMapForValues(result, docToTerms[docBase + i]);
    }

    @Override
    public void setScorer(Scorer scorer) throws IOException
    {
        // Do nothing
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException
    {
        this.docBase = docBase;
    }

    @Override
    public boolean acceptsDocsOutOfOrder()
    {
        return true;
    }

    private void adjustMapForValues(Map<Object, Integer> map, Collection<String> terms)
    {
        if (terms == null)
        {
            return;
        }
        for (String term : terms)
        {
            Object object = statisticsMapper.getValueFromLuceneField(term);
            Integer count = map.get(object);

            if (count == null)
            {
                count = 0;
            }
            map.put(object, count + 1);
        }
    }
}