package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;

import java.util.ArrayList;
import java.util.List;

import io.github.proify.lyricon.lyric.model.LyricWord;
import io.github.proify.lyricon.lyric.model.RichLyricLine;
import io.github.proify.lyricon.lyric.model.Song;


final class ParsedLyrics {

    final String trackKey;
    final String title;
    final String artist;
    final long durationMs;
    final List<TimedLine> lines;
    final boolean hasTranslation;
    final boolean hasWordTiming;
    private final int sourceHash;

    ParsedLyrics(String trackKey, String title, String artist, long durationMs,
                 List<TimedLine> lines, boolean hasTranslation, boolean hasWordTiming,
                 int sourceHash) {
        this.trackKey = trackKey;
        this.title = title;
        this.artist = artist;
        this.durationMs = durationMs;
        this.lines = lines;
        this.hasTranslation = hasTranslation;
        this.hasWordTiming = hasWordTiming;
        this.sourceHash = sourceHash;
    }

    String dedupeKey() {
        return trackKey + "|" + sourceHash + "|" + lines.size() + "|" + durationMs;
    }

    boolean hasSyntheticWords() {
        for (TimedLine line : lines) {
            if (!line.hasWords() && line.text != null && !line.text.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    
    Song toSong() {
        List<RichLyricLine> output = new ArrayList<>(lines.size());
        for (TimedLine line : lines) {
            List<LyricWord> words = null;
            if (line.hasWords()) {
                words = new ArrayList<>(line.words.size());
                for (TimedWord word : line.words) {
                    long begin = Math.max(line.begin, word.begin);
                    long end = word.end > begin ? Math.min(line.end, word.end) : Math.min(line.end, begin + 1);
                    if (end <= begin) {
                        end = begin + 1;
                    }
                    words.add(new LyricWord(begin, end, end - begin,
                            word.text == null ? "" : word.text, null));
                }
            } else {
                words = synthesizeWords(line);
            }
            output.add(new RichLyricLine(
                    line.begin,
                    line.end,
                    line.end - line.begin,
                    false,
                    null,
                    line.text,
                    words,
                    null,
                    null,
                    line.translation,
                    null,
                    null));
        }
        return new Song(trackKey, title, artist, durationMs, null, output);
    }

    




    private static List<LyricWord> synthesizeWords(TimedLine line) {
        String text = line.text;
        if (text == null || text.isEmpty() || line.end <= line.begin) {
            return null;
        }
        List<String> units = splitUnits(text);
        if (units.size() < 2) {
            return null;
        }
        int totalWeight = 0;
        for (String unit : units) {
            totalWeight += unitWeight(unit);
        }
        if (totalWeight <= 0) {
            totalWeight = units.size();
        }
        long cursor = line.begin;
        long consumed = 0L;
        List<LyricWord> words = new ArrayList<>(units.size());
        for (int i = 0; i < units.size(); i++) {
            String unit = units.get(i);
            boolean last = i == units.size() - 1;
            long end;
            if (last) {
                end = line.end;
            } else {
                consumed += unitWeight(unit);
                end = line.begin + Math.max(1L, (line.end - line.begin) * consumed / totalWeight);
            }
            end = Math.min(line.end, Math.max(cursor + 1L, end));
            words.add(new LyricWord(cursor, end, end - cursor, unit, null));
            cursor = end;
        }
        return words.isEmpty() ? null : words;
    }

    

    private static List<String> splitUnits(String text) {
        List<String> units = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int cp = text.codePointAt(index);
            int len = Character.charCount(cp);
            boolean latinWord = cp < 0x2E80 && Character.isLetterOrDigit(cp);
            if (latinWord) {
                int start = index;
                index += len;
                while (index < text.length()) {
                    int next = text.codePointAt(index);
                    if (next < 0x2E80 && Character.isLetterOrDigit(next)) {
                        index += Character.charCount(next);
                    } else {
                        break;
                    }
                }
                units.add(text.substring(start, index));
                continue;
            }
            String piece = text.substring(index, index + len);
            boolean joinPrevious = Character.isWhitespace(cp) || isPunctuation(cp);
            if (!units.isEmpty() && joinPrevious) {
                units.set(units.size() - 1, units.get(units.size() - 1) + piece);
            } else {
                units.add(piece);
            }
            index += len;
        }
        return units;
    }

    private static int unitWeight(String unit) {
        if (unit == null) {
            return 0;
        }
        int weight = 0;
        int index = 0;
        while (index < unit.length()) {
            int cp = unit.codePointAt(index);
            if (!Character.isWhitespace(cp)) {
                weight++;
            }
            index += Character.charCount(cp);
        }
        return weight > 0 ? weight : 1;
    }

    private static boolean isPunctuation(int cp) {
        switch (Character.getType(cp)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
                return true;
            default:
                return false;
        }
    }
}
