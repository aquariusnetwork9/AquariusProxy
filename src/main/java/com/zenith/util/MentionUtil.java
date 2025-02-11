package com.zenith.util;

public final class MentionUtil {
    public static final String EVERYONE = "@everyone";
    public static final String HERE = "@here";

    private MentionUtil() {
    }

    public static String forChannel(String id) {
        return "<#" + id + ">";
    }

    public static String forRole(String id) {
        return "<@&" + id + ">";
    }

    public static String forUser(String id) {
        return "<@" + id + ">";
    }
}
