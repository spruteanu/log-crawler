package org.prismus.scrambler.log

import spock.lang.Specification

class DummyConsumerTest extends Specification {

    void 'verify registered log extended consumer builders'() {
        final crawlerBuilder = LogCrawler.builder('/sample-ext-log.groovy')
        final registeredBuilders = crawlerBuilder.provider.getRegisteredBuilders()
        LogEntry entry = new LogEntry('vasilica petrica')
        LogCrawler crawler

        expect:
        1 == registeredBuilders.size()

        and: 'verify that registered dummy consumer is built and consumed'
        null != (crawler = crawlerBuilder.build())
        null != crawler
        crawler.consume(entry)
        'test' == entry.get('dummy')
    }

}
