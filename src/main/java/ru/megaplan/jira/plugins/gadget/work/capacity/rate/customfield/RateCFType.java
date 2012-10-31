package ru.megaplan.jira.plugins.gadget.work.capacity.rate.customfield;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.converters.DoubleConverter;
import com.atlassian.jira.issue.customfields.impl.NumberCFType;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.fields.CustomField;
import org.apache.log4j.Logger;

public class RateCFType extends NumberCFType
{

    private final static Logger log = Logger.getLogger(RateCFType.class);

    public RateCFType(CustomFieldValuePersister customFieldValuePersister, DoubleConverter doubleConverter, GenericConfigManager genericConfigManager) {
        super(customFieldValuePersister, doubleConverter, genericConfigManager);
    }

    /* @Override
      public String getStringFromSingularObject(Double singularObject) {
          return null;
      }       */

    @Override
    public void updateValue(CustomField field, Issue issue, Double value) {
        Object o = issue.getCustomFieldValue(field);
        Double doubleValue = (Double) o;
        doubleValue += value;
        super.updateValue(field,issue,doubleValue);
    }

  /*  @Override
    public Double getSingularObjectFromString(String dbValue) throws FieldValidationException {
        if ((dbValue == null) || (dbValue.length() == 0))
        {
            log.debug("Returning null on no db value");
            return null;
        }
        AgreementOfAnIssue obj = null;
        try {
            obj = this.valueSerializer.unserialize(dbValue);
        } catch (VertygoSLAException e) {
            log.debug("Unable to parse db value '" + dbValue + "'");
        }
        log.debug("getSingularObjectFromString() - param given : " + dbValue + ", return : " + obj);
        return obj;
    }       */

}