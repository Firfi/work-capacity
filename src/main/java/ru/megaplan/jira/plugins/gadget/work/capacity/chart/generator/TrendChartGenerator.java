package ru.megaplan.jira.plugins.gadget.work.capacity.chart.generator;

import com.atlassian.jira.charts.jfreechart.ChartGenerator;
import com.atlassian.jira.charts.jfreechart.ChartHelper;
import com.atlassian.jira.charts.jfreechart.util.ChartDefaults;
import com.atlassian.jira.charts.jfreechart.util.ChartUtil;
import com.atlassian.jira.web.bean.I18nBean;
import org.apache.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.TickUnitSource;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;

import java.awt.geom.Ellipse2D;
import java.util.List;

/**
 * @since v4.0
 */
public class TrendChartGenerator implements ChartGenerator
{
    private static final Logger log = Logger.getLogger(TrendChartGenerator.class);
    private final TimeSeries trendSeries;
    private I18nBean i18nBean;
    private XYDataset createdVsResolved;

    public TrendChartGenerator(XYDataset createdVsResolved,TimeSeries trendSeries,I18nBean i18nBean)
    {
        this.trendSeries = trendSeries;
        this.i18nBean = i18nBean;
        this.createdVsResolved = createdVsResolved;
    }

    public ChartHelper generateChart()
    {
        boolean legend = false;
        boolean tooltips = true;
        boolean urls = true;

        JFreeChart chart = org.jfree.chart.ChartFactory.createTimeSeriesChart(null, null, null, createdVsResolved, legend, tooltips, urls);
        setTimeSeriesChartDefaults(chart, chart.getXYPlot(), i18nBean);
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        TickUnitSource units = NumberAxis.createIntegerTickUnits();
        yAxis.setStandardTickUnits(units);

        XYDifferenceRenderer xyDifferenceRenderer = new XYDifferenceRenderer(ChartDefaults.RED_DIFF, ChartDefaults.GREEN_DIFF, true);
        xyDifferenceRenderer.setSeriesPaint(0, ChartDefaults.RED);
        xyDifferenceRenderer.setSeriesPaint(1, ChartDefaults.BRIGHT_GREEN);
        xyDifferenceRenderer.setShape(new Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0));
        xyDifferenceRenderer.setBaseStroke(ChartDefaults.defaultStroke);
        xyDifferenceRenderer.setStroke(ChartDefaults.defaultStroke);

        plot.setRenderer(xyDifferenceRenderer);

        // add 3rd series as subplot
        XYPlot subPlot = null;
        //if (trendSeries != null)
        //{
            XYLineAndShapeRenderer xyLineAndShapeRenderer = new XYLineAndShapeRenderer(true, true);
            xyLineAndShapeRenderer.setShape(xyDifferenceRenderer.getSeriesShape(0));

            TimeSeriesCollection subDataset = new TimeSeriesCollection();
            subDataset.addSeries(trendSeries);

            ValueAxis domainAxis = plot.getDomainAxis();
            ValueAxis rangeAxis = null;
            try
            {
                rangeAxis = (ValueAxis) plot.getRangeAxis().clone();
            }
            catch (CloneNotSupportedException ex)
            {
                log.error("Failed to clone Y axis", ex);
            }
            subPlot = new XYPlot(subDataset, domainAxis, rangeAxis, xyLineAndShapeRenderer);

            CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(plot.getDomainAxis());
            combinedPlot.setRenderer(plot.getRenderer());
            combinedPlot.setGap(10.0);
            //combinedPlot.add(plot, 3);
            combinedPlot.add(subPlot);
            subPlot.setOutlineStroke(null);

            // replace the original chart with the combined one
            chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, false);
            setTimeSeriesChartDefaults(chart, subPlot, i18nBean);
       // }


        return new ChartHelper(chart);
    }

    /**
     * Utility method to set the default style of the Time Series Charts
     *
     * @param chart {@link JFreeChart} to style
     * @param genericPlot {@link Plot}
     * @param i18nBean an i18nBean with the remote user
     */
    private static void setTimeSeriesChartDefaults(JFreeChart chart, Plot genericPlot, final I18nBean i18nBean)
    {
        ChartUtil.setDefaults(chart, i18nBean);

        XYPlot plot = (XYPlot) genericPlot;

        // renderer
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setBaseItemLabelFont(ChartDefaults.defaultFont);
        renderer.setBaseItemLabelPaint(ChartDefaults.axisLabelColor);
        for (int j = 0; j < ChartDefaults.darkColors.length; j++)
        {
            renderer.setSeriesPaint(j, ChartDefaults.darkColors[j]);
            renderer.setSeriesStroke(j, ChartDefaults.defaultStroke);
        }
        renderer.setBaseShapesVisible(false);
        renderer.setBaseStroke(ChartDefaults.defaultStroke);
    }
}