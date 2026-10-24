package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;

import java.util.List;

final class TimedLine {

    final long begin;
    long end = -1L;
    final String text;
    List<TimedWord> words;
    
    long terminalEnd = -1L;
    String translation;

    TimedLine(long begin, long end, String text, List<TimedWord> words) {
        this.begin = begin;
        this.end = end;
        this.text = text;
        this.words = words;
    }

    boolean hasWords() {
        return words != null && !words.isEmpty();
    }
}
