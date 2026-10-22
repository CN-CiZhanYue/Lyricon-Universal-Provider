package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;












final class LrcParser {

    private static final Pattern LINE_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    
    private static final Pattern WORD_TAG =
            Pattern.compile("[<\\[](\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?[>\\]]");

    private LrcParser() {
    }

    static List<TimedLine> parse(String source) {
        List<TimedLine> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (String physicalLine : source.split("\\r\\n|\\n|\\r")) {
            String line = physicalLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<Long> stamps = new ArrayList<>(1);
            int contentStart = leadingTimestamps(line, stamps);
            if (stamps.isEmpty()) {
                continue;
            }
            String content = line.substring(contentStart);
            if (content.isEmpty()) {
                continue;
            }
            for (long stamp : stamps) {
                TimedLine parsed = parseContent(stamp, content);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        }
        result.sort(Comparator.comparingLong(value -> value.begin));
        return result;
    }

    
    private static int leadingTimestamps(String line, List<Long> out) {
        Matcher matcher = LINE_TAG.matcher(line);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() != cursor) {
                break;
            }
            out.add(toMillis(matcher.group(1), matcher.group(2), matcher.group(3)));
            cursor = matcher.end();
        }
        return cursor;
    }

    private static TimedLine parseContent(long lineBegin, String content) {
        Matcher matcher = WORD_TAG.matcher(content);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            ends.add(matcher.end());
            times.add(toMillis(matcher.group(1), matcher.group(2), matcher.group(3)));
        }

        StringBuilder text = new StringBuilder(content.length());
        List<TimedWord> words = null;
        long terminalEnd = -1L;

        if (starts.isEmpty()) {
            text.append(content);
        } else {
            words = new ArrayList<>(starts.size() + 1);
            int firstTag = starts.get(0);
            if (firstTag > 0) {
                String leading = content.substring(0, firstTag);
                text.append(leading);
                if (!leading.trim().isEmpty()) {
                    words.add(new TimedWord(lineBegin, absoluteWordTime(times.get(0), lineBegin), leading));
                }
            }
            for (int i = 0; i < starts.size(); i++) {
                int from = ends.get(i);
                int to = i + 1 < starts.size() ? starts.get(i + 1) : content.length();
                String segment = content.substring(from, to);
                text.append(segment);
                long begin = absoluteWordTime(times.get(i), lineBegin);
                long end = i + 1 < times.size()
                        ? absoluteWordTime(times.get(i + 1), lineBegin)
                        : -1L;
                if (segment.isEmpty() && i == starts.size() - 1) {
                    terminalEnd = begin;
                } else {
                    words.add(new TimedWord(begin, end, segment));
                }
            }
            if (words.isEmpty()) {
                words = null;
            }
        }

        String plain = text.toString().trim();
        if (plain.isEmpty()) {
            return null;
        }
        TimedLine line = new TimedLine(lineBegin, -1L, plain, words);
        line.terminalEnd = terminalEnd;
        return line;
    }

    



    private static long absoluteWordTime(long time, long lineBegin) {
        if (time + 1000L < lineBegin) {
            return lineBegin + time;
        }
        return Math.max(time, lineBegin);
    }

    private static long toMillis(String minutes, String seconds, String fraction) {
        long value = Long.parseLong(minutes) * 60_000L + Long.parseLong(seconds) * 1000L;
        if (fraction != null && !fraction.isEmpty()) {
            int digits = fraction.length();
            long raw = Long.parseLong(fraction);
            if (digits <= 2) {
                raw *= digits == 1 ? 100L : 10L;
            } else if (digits > 3) {
                raw /= (long) Math.pow(10, digits - 3);
            }
            value += raw;
        }
        return value;
    }
}
