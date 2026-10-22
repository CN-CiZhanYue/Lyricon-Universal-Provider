package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;


final class TimedWord {

    long begin;
    long end;
    final String text;

    TimedWord(long begin, long end, String text) {
        this.begin = begin;
        this.end = end;
        this.text = text;
    }
}
