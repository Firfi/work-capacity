package ru.megaplan.jira.plugins.gadget.work.capacity.resource;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 10.07.12
 * Time: 19:13
 * To change this template use File | Settings | File Templates.
 */
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.FIELD)
public class Status {
    @XmlAttribute
    private String value;
    @XmlAttribute
    private String label;

    public Status(String key, String name) {
        this.value = key;
        this.label = name;
    }

    public String getKey() {
        return value;
    }

    public void setKey(String key) {
        this.value = key;
    }

    public String getName() {
        return label;
    }

    public void setName(String name) {
        this.label = name;
    }
}
