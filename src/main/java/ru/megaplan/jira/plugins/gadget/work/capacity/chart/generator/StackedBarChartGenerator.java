package ru.megaplan.jira.plugins.gadget.work.capacity.chart.generator;

import com.atlassian.jira.charts.jfreechart.ChartGenerator;
import com.atlassian.jira.charts.jfreechart.ChartHelper;
import com.atlassian.jira.charts.jfreechart.util.ChartDefaults;
import com.atlassian.jira.charts.jfreechart.util.ChartUtil;
import com.atlassian.jira.web.bean.I18nBean;
import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.*;
import org.jfree.chart.labels.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.chart.title.LegendGraphic;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;

import java.awt.*;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Creates Histogram charts.  These are essentially bar charts, however histograms in JFreechart deal better with dates
 * along the X-axis.
 *
 * @since v4.0
 */
public class StackedBarChartGenerator implements ChartGenerator
{

    private final static Logger log = Logger.getLogger(StackedBarChartGenerator.class);

    private final CategoryDataset dataset;
    private I18nBean i18nBean;
    private CategoryDataset trendSeries;
    private CategoryDataset assigneeDetailsDatasetCreated;
    private CategoryDataset assigneeDetailsDatasetResolved;

    public StackedBarChartGenerator(CategoryDataset dataset, CategoryDataset trendSeries, CategoryDataset assigneeDetailsDatasetCreated, CategoryDataset assigneeDetailsDatasetResolved, final I18nBean i18nBean)
    {
        this.dataset = dataset;
        this.i18nBean = i18nBean;
        this.trendSeries = trendSeries;
        this.assigneeDetailsDatasetCreated = assigneeDetailsDatasetCreated;
        this.assigneeDetailsDatasetResolved = assigneeDetailsDatasetResolved;
    }

    public ChartHelper generateChart()
    {
        boolean legend = true;
        boolean tooltips = false;
        boolean urls = false;

        JFreeChart chart = ChartFactory.createStackedBarChart(null, null, "FACTS", dataset, PlotOrientation.VERTICAL, legend, tooltips, urls);

        setStackedBarChartDefaults(chart, i18nBean);

        CategoryPlot plot = chart.getCategoryPlot();





        NumberAxis axis = (NumberAxis) plot.getRangeAxis();
        TickUnitSource units = NumberAxis.createIntegerTickUnits();
        axis.setStandardTickUnits(units);

        CategoryAxis catAxis = plot.getDomainAxis();
        catAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);

        plot.getRenderer().setSeriesItemLabelsVisible(0, true);
        plot.getRenderer().setSeriesItemLabelPaint(0, Color.BLACK);
        plot.getRenderer().setSeriesPositiveItemLabelPosition(0, new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER, TextAnchor.CENTER, -Math.PI / 2));
        plot.getRenderer().setSeriesNegativeItemLabelPosition(0, plot.getRenderer().getSeriesPositiveItemLabelPosition(0));
        plot.getRenderer().setSeriesNegativeItemLabelPosition(1, new ItemLabelPosition(ItemLabelAnchor.OUTSIDE6, TextAnchor.TOP_CENTER,  TextAnchor.CENTER, -Math.PI / 2));
        plot.getRenderer().setSeriesPositiveItemLabelPosition(1, plot.getRenderer().getSeriesNegativeItemLabelPosition(1));
        plot.getRenderer().setSeriesItemLabelsVisible(1, true);
        plot.getRenderer().setSeriesItemLabelPaint(1, Color.BLUE);

        if (assigneeDetailsDatasetCreated != null && assigneeDetailsDatasetResolved != null && assigneeDetailsDatasetCreated.getRowCount() > 1) {
            BarRenderer renderer2 = new BarRenderer();
            renderer2.setShadowVisible(false);
            renderer2.setItemMargin(0.05); // pupa card

            plot.setDataset(1, assigneeDetailsDatasetCreated);

            plot.setRenderer(1, renderer2);
            plot.setDataset(2, assigneeDetailsDatasetResolved);
            plot.setRenderer(2, renderer2);
        }



        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);


        //plot.getRenderer().setSeriesOutlinePaint(0, ChartDefaults.RED_DIFF);
       // plot.getRenderer().setSeriesPaint(0, ChartDefaults.RED_DIFF);


        if (trendSeries != null) {

            CategoryPlot subPlot2 = getSubPlot(plot, trendSeries, "PERCENTS");


            subPlot2.getRenderer().setSeriesItemLabelGenerator(0, new CategoryItemLabelGenerator() {
                @Override
                public String generateRowLabel(CategoryDataset categoryDataset, int i) {
                    return null;
                }

                @Override
                public String generateColumnLabel(CategoryDataset categoryDataset, int i) {
                    return null;
                }

                @Override
                public String generateLabel(CategoryDataset categoryDataset, int i, int i1) {
                    return Integer.toString(categoryDataset.getValue(i, i1).intValue());
                }
            });
            subPlot2.getRenderer().setSeriesItemLabelsVisible(0, true);
            subPlot2.getRenderer().setSeriesPositiveItemLabelPosition(0, new ItemLabelPosition(ItemLabelAnchor.OUTSIDE10, TextAnchor.CENTER, TextAnchor.CENTER_RIGHT, - Math.PI / 2));


            CombinedDomainCategoryPlot combinedPlot = getCombinedPlot(plot, subPlot2);

            chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);

        }

        return new ChartHelper(chart);
    }

    private CombinedDomainCategoryPlot getCombinedPlot(final CategoryPlot plot, CategoryPlot subPlot2) {
        CombinedDomainCategoryPlot combinedPlot = new CombinedDomainCategoryPlot(plot.getDomainAxis()) {
            @Override
            public LegendItemCollection getLegendItems() {
                LegendItemCollection resultUnfiltered = super.getLegendItems();
                LegendItemCollection result = new LegendItemCollection();
                Set<String> labelsSet = new HashSet<String>();
                for (int i = 0; i < resultUnfiltered.getItemCount(); ++i) {
                    LegendItem legendItem = resultUnfiltered.get(i);
                    if (labelsSet.contains(legendItem.getLabel())) continue;
                    labelsSet.add(legendItem.getLabel());
                    result.add(legendItem);
                }
                return result;
            }
        };
        combinedPlot.setRenderer(plot.getRenderer());
        //
        combinedPlot.add(plot,4);
        combinedPlot.setGap(20.0);
        combinedPlot.add(subPlot2);
        subPlot2.setOutlineStroke(null);
        combinedPlot.setDataset(plot.getDataset());
        return combinedPlot;  //To change body of created methods use File | Settings | File Templates.
    }

    private CategoryPlot getSubPlot(CategoryPlot plot, CategoryDataset dataset, String label) {
        BarRenderer subRenderer = new StackedBarRenderer();
        //subRenderer.setShape(plot.getRenderer().getSeriesShape(0));
        ValueAxis subAxis;
        try {
            subAxis = (ValueAxis) plot.getRangeAxis().clone();
            subAxis.setLabel(label);
        } catch (CloneNotSupportedException e) {
            log.error("error in cloning axis; ",e);
            throw new RuntimeException(e);
        }
        return new CategoryPlot(dataset, plot.getDomainAxis(), subAxis, subRenderer);
    }

    /**
     * Utility method to set the default style of the Bar Charts
     *
     * @param chart {@link JFreeChart} to style
     * @param i18nBean an i18nBean with the remote user
     */
    private static void setStackedBarChartDefaults(JFreeChart chart, final I18nBean i18nBean)
    {
        ChartUtil.setDefaults(chart, i18nBean);

        CategoryPlot plot = (CategoryPlot) chart.getPlot();

        plot.setAxisOffset(new RectangleInsets(1.0, 1.0, 1.0, 1.0));

        // renderer
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setBaseItemLabelFont(ChartDefaults.defaultFont);
        renderer.setBaseItemLabelsVisible(false);
        renderer.setItemMargin(0.2);

        renderer.setBasePositiveItemLabelPosition(
                new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER));
        renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setBaseItemLabelPaint(ChartDefaults.axisLabelColor);

        StandardCategoryToolTipGenerator generator =
                new StandardCategoryToolTipGenerator("{1}, {2}", NumberFormat.getInstance());
        renderer.setBaseToolTipGenerator(generator);
        renderer.setDrawBarOutline(false);
        for (int j = 0; j < ChartDefaults.darkColors.length; j++)
        {
            renderer.setSeriesPaint(j, ChartDefaults.darkColors[j]);
        }
    }
}