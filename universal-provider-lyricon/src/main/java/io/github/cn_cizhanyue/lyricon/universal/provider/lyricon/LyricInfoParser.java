package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class LyricInfoParser {

    static final String LYRIC_INFO_KEY = "lyricInfo";

    private static final String[] TRANSLATION_KEYS = {
            "translationLyric",
            "translatedLyric",
            "translateLyric",
            "transLyric",
            "lyricTranslation",
            "translationLrc",
            "transLrc",
            "translation"
    };

    private LyricInfoParser() {
    }

    static ParsedLyrics parse(String value, String fallbackTitle, String fallbackArtist,
                              long fallbackDuration) {
        if (value == null) {
            return null;
        }
        String json = value.trim();
        if (json.isEmpty()) {
            return null;
        }
        JSONObject object;
        try {
            object = new JSONObject(json);
        } catch (Throwable ignored) {
            return null;
        }
        if (object.optBoolean("noLyric", false)) {
            return null;
        }

        String lyricLane = option(object, "lyric");
        String wordLane = option(object, "rawLyric");
        String translationLane = firstTranslationLane(object);

        List<TimedLine> lineLane = LrcParser.parse(lyricLane);
        List<TimedLine> wordLines = LrcParser.parse(wordLane);
        List<TimedLine> lines = mergeLanes(lineLane, wordLines);
        if (lines.isEmpty()) {
            return null;
        }

        lines.sort((left, right) -> Long.compare(left.begin, right.begin));
        dropSyntheticWordTiming(lines);
        applyTimings(lines, fallbackDuration);
        boolean hasTranslation = applyTranslations(lines, LrcParser.parse(translationLane));
        boolean hasWords = false;
        for (TimedLine line : lines) {
            if (line.hasWords()) {
                hasWords = true;
                break;
            }
        }

        String title = firstNonEmpty(option(object, "songName"), fallbackTitle);
        String artist = firstNonEmpty(option(object, "artist"), fallbackArtist);
        long duration = fallbackDuration > 0L ? fallbackDuration : object.optLong("duration", 0L);
        String trackKey = firstNonEmpty(option(object, "trackKey"), option(object, "songId"));
        if (trackKey.isEmpty()) {
            trackKey = title + "|" + artist + "|" + duration;
        }

        int sourceHash = (lyricLane + "\u0000" + wordLane + "\u0000" + translationLane).hashCode();
        return new ParsedLyrics(trackKey, title, artist, duration, lines,
                hasTranslation, hasWords, sourceHash);
    }

    private static List<TimedLine> mergeLanes(List<TimedLine> lineLane, List<TimedLine> wordLane) {
        TreeMap<Long, TimedLine> merged = new TreeMap<>();
        for (TimedLine line : lineLane) {
            if (!merged.containsKey(line.begin)) {
                merged.put(line.begin, line);
            }
        }
        if (wordLane.isEmpty()) {
            return new ArrayList<>(merged.values());
        }

        Map<Long, TimedLine> wordLinesByBegin = new HashMap<>();
        for (TimedLine line : wordLane) {
            wordLinesByBegin.put(line.begin, line);
        }
        for (TimedLine line : new ArrayList<>(merged.values())) {
            TimedLine wordLine = wordLinesByBegin.get(line.begin);
            if (wordLine == null) {
                wordLine = nearest(wordLane, line.begin, 120L);
            }
            if (wordLine != null && wordLine.hasWords() && !line.hasWords()) {
                line.words = wordLine.words;
                if (wordLine.terminalEnd > 0L) {
                    line.terminalEnd = wordLine.terminalEnd;
                }
            }
        }
        for (TimedLine wordLine : wordLane) {
            if (!merged.containsKey(wordLine.begin)) {
                merged.put(wordLine.begin, wordLine);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static void dropSyntheticWordTiming(List<TimedLine> lines) {
        for (TimedLine line : lines) {
            if (line.words != null && line.words.size() < 2) {
                line.words = null;
            }
        }
    }

    private static void applyTimings(List<TimedLine> lines, long durationMs) {
        int size = lines.size();
        for (int i = 0; i < size; i++) {
            TimedLine line = lines.get(i);
            long end;
            if (i + 1 < size) {
                end = lines.get(i + 1).begin;
            } else if (line.terminalEnd > line.begin) {
                end = line.terminalEnd;
            } else {
                end = line.begin + 4000L;
                if (line.hasWords()) {
                    TimedWord last = line.words.get(line.words.size() - 1);
                    if (last.end > end) {
                        end = last.end;
                    }
                }
            }
            if (end <= line.begin) {
                end = line.begin + 500L;
            }
            if (durationMs > 0L && line.begin < durationMs) {
                end = Math.min(end, Math.max(line.begin + 1L, durationMs));
            }
            line.end = Math.max(end, line.begin + 1L);
            finalizeWords(line);
        }
    }

    private static void finalizeWords(TimedLine line) {
        if (!line.hasWords()) {
            return;
        }
        
        line.words.sort((left, right) -> Long.compare(left.begin, right.begin));
        int size = line.words.size();
        long[] begins = new long[size];
        long[] ends = new long[size];
        long cursor = line.begin;
        for (int i = 0; i < size; i++) {
            TimedWord word = line.words.get(i);
            long begin = Math.max(cursor, Math.max(line.begin, Math.min(word.begin, line.end - 1L)));
            long end;
            if (word.end > begin) {
                end = Math.min(word.end, line.end);
            } else if (line.terminalEnd > begin) {
                end = Math.min(line.terminalEnd, line.end);
            } else {
                end = line.end;
            }
            if (end <= begin) {
                end = Math.min(line.end, begin + 1L);
            }
            begins[i] = begin;
            ends[i] = end;
            cursor = begin;
        }
        for (int i = 0; i < size; i++) {
            TimedWord word = line.words.get(i);
            long begin = begins[i];
            long end = ends[i];
            if (i + 1 < size && begins[i + 1] > begin && begins[i + 1] < end) {
                end = begins[i + 1];
            }
            if (end <= begin) {
                end = begin + 1L;
            }
            word.begin = begin;
            word.end = end;
        }
    }

    private static boolean applyTranslations(List<TimedLine> lines, List<TimedLine> translationLane) {
        if (translationLane.isEmpty()) {
            return false;
        }
        Map<Long, String> byBegin = new HashMap<>();
        for (TimedLine line : translationLane) {
            if (!line.text.isEmpty()) {
                byBegin.put(line.begin, line.text);
            }
        }
        boolean indexFallback = translationLane.size() == lines.size();
        boolean any = false;
        for (int i = 0; i < lines.size(); i++) {
            TimedLine line = lines.get(i);
            String text = byBegin.get(line.begin);
            if (text == null && indexFallback) {
                TimedLine candidate = translationLane.get(i);
                if (Math.abs(candidate.begin - line.begin) <= 2000L) {
                    text = candidate.text;
                }
            }
            if (text == null) {
                TimedLine candidate = nearest(translationLane, line.begin, 150L);
                if (candidate != null) {
                    text = candidate.text;
                }
            }
            if (text == null) {
                continue;
            }
            text = text.trim();
            if (!text.isEmpty() && !text.equals(line.text)) {
                line.translation = text;
                any = true;
            }
        }
        return any;
    }

    private static TimedLine nearest(List<TimedLine> lines, long target, long tolerance) {
        TimedLine best = null;
        long bestDistance = Long.MAX_VALUE;
        for (TimedLine line : lines) {
            long distance = Math.abs(line.begin - target);
            if (distance <= tolerance && distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static String firstTranslationLane(JSONObject object) {
        for (String key : TRANSLATION_KEYS) {
            String value = option(object, key);
            if (!value.isEmpty() && !LrcParser.parse(value).isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String option(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return "";
        }
        String value = object.optString(key, "");
        if (value == null || "null".equals(value)) {
            return "";
        }
        return value;
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return "";
    }
}
