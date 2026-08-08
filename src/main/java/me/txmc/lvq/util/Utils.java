package me.txmc.lvq.util;

public class Utils {
    public static String getFormattedInterval(long ms) {
        long seconds = ms / 1000L % 60L;
        long minutes = ms / 60000L % 60L;
        long hours = ms / 3600000L % 24L;
        long days = ms / 86400000L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("D ");
        if (hours > 0) sb.append(hours).append("H ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 && sb.length() == 0) sb.append(seconds).append("s");
        return sb.length() == 0 ? "now" : sb.toString().trim();
    }
}
