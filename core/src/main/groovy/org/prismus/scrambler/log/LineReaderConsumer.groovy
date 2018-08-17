package org.prismus.scrambler.log

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

@CompileStatic
@PackageScope
class LineReaderConsumer implements LogConsumer {
    final LogCrawler logCrawler
    final LogConsumer sourceConsumer

    LineReaderConsumer(LogCrawler logCrawler, LogConsumer sourceConsumer) {
        this.sourceConsumer = sourceConsumer
        this.logCrawler = logCrawler
    }

    @Override
    void consume(LogEntry entry) {
        logCrawler.consume(LineReader.toLineReader(entry), LineReader.getSourceName(entry), sourceConsumer)
    }
}
