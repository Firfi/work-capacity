package ru.megaplan.jira.plugins.gadget.work.capacity.rate.util;

import java.util.HashSet;
import java.util.Set;

public final class ObjectUtils
{
    public static int safeHash(Object aObj)
    {
        if (aObj == null) {
            return 0;
        }
        return aObj.hashCode();
    }

    public static boolean safeEquals(Object aObj1, Object aObj2)
    {
        return (aObj1 == aObj2) || ((aObj1 != null) && (aObj1.equals(aObj2)));
    }

    public static Set<Long> copyLongs(Set<Long> aOrigin)
    {
        if (aOrigin == null) {
            return null;
        }
        Set result = new HashSet();
        for (Long l : aOrigin) {
            result.add(l);
        }
        return result;
    }

    public static Set<String> copyStrings(Set<String> aOrigin)
    {
        if (aOrigin == null) {
            return null;
        }
        Set result = new HashSet();
        for (String l : aOrigin) {
            result.add(l);
        }
        return result;
    }
}