/*
 * Log crawler, tool that allows to extract/crawl log files for further analysis
 *
 * Copyright (c) 2015, Sergiu Prutean. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package org.prismus.scrambler.log

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Log
import org.apache.commons.lang3.time.DurationFormatUtils

import javax.sql.DataSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate

/**
 * @author Serge Pruteanu
 */
@CompileStatic
@Log
class LogCrawler implements Iterable<LogEntry> {

    protected Map<LogEntry, LogConsumer> sourceConsumerMap = [:]
    private List<LogConsumer> consumers = new ArrayList<LogConsumer>()
    private CloseableContainer closeableContainer = new CloseableContainer()

    AtomicBoolean processContext = new AtomicBoolean(true)

    boolean multiline = true

    @PackageScope
    LogCrawler() {
    }

    LogCrawler oneLineEntry() {
        multiline = false
        return this
    }

    LogCrawler source(LogEntry source, LogConsumer sourceConsumer) {
        sourceConsumerMap.put(source, sourceConsumer)
        return this
    }

    @SuppressWarnings("GroovySynchronizationOnNonFinalField")
    LogCrawler stop() {
        processContext.set(false)
        return this
    }

    protected void checkCloseable(Object object) {
        if (object instanceof Closeable) {
            if (object instanceof CloseableContainer) {
                ((CloseableContainer) object).addAll(closeableContainer)
                closeableContainer = object as CloseableContainer
            } else {
                closeableContainer.add(object as Closeable)
            }
        }
    }

    LogCrawler using(LogConsumer consumer) {
        consumers.add(consumer)
        checkCloseable(consumer)
        if (consumer instanceof CsvWriterConsumer) {
            final csvOutputConsumer = (CsvWriterConsumer) consumer
            if (!csvOutputConsumer.columns) {
                final columns = new LinkedHashSet<String>()
                for (LogConsumer sourceConsumer : sourceConsumerMap.values()) {
                    sourceConsumer = lookupWrapped(sourceConsumer)
                    if (sourceConsumer instanceof RegexConsumer) {
                        columns.addAll(((RegexConsumer) sourceConsumer).groupIndexMap.keySet().toList())
                    }
                }
                if (!columns) {
                    throw new RuntimeException('Columns are not defined for CsvOutputConsumer')
                }
                csvOutputConsumer.columns = columns.toList()
            }
        }
        return this
    }

    LogCrawler filter(Predicate predicate, LogConsumer consumer, LogConsumer... consumers) {
        def cs = consumers ? ContainerConsumer.of(consumer).addAll(consumers) : consumer
        return using(new PredicateConsumer(predicate, cs))
    }

    LogCrawler filter(@DelegatesTo(LogEntry) Closure<Boolean> predicate, @DelegatesTo(LogEntry) Closure consumer, @DelegatesTo(LogEntry) Closure... consumers) {
        def cs = consumers ? ContainerConsumer.of(consumer).addAll(consumers) : new ClosureConsumer(consumer)
        return filter(new ClosurePredicate(predicate), cs)
    }

    LogCrawler toDb(DataSource dataSource, String tableName = 'LogEntry', String... columns) {
        return using(TableBatchConsumer.of(dataSource, tableName, columns))
    }

    protected void consume(LogEntry logEntry, LogConsumer sourceConsumer) {
        if (logEntry) {
            logEntry.sourceInfo("${Objects.toString(logEntry.source, '')}:($logEntry.row)".toString())
            sourceConsumer.consume(logEntry)
            for (LogConsumer consumer : consumers) {
                consumer.consume(logEntry)
            }
        }
    }

    protected int consume(LineReader lineReader, String sourceName, LogConsumer sourceConsumer) {
        try {
            LogEntry lastEntry = null
            int currentRow = 0
            int nEntries = 0
            String line
            log.info("Consuming '$sourceName'")
            while ((line = lineReader.readLine()) != null) {
                final logEntry = new LogEntry(sourceName, line, ++currentRow)
                sourceConsumer.consume(logEntry)
                if (logEntry.isEmpty()) {
                    if (multiline && lastEntry) {
                        lastEntry.line += LineReader.LINE_BREAK + line
                    }
                } else {
                    nEntries++
                    consume(lastEntry, sourceConsumer)
                    lastEntry = logEntry
                }
            }
            consume(lastEntry, sourceConsumer)
            log.info("Done consuming '$sourceName'. Processed '$currentRow' rows, consumed '$nEntries' entries")
            return nEntries
        } finally {
            Utils.closeQuietly(lineReader)
        }
    }

    protected void close() {
        closeableContainer.close()
    }

    void consume(LogEntry logEntry) {
        for (LogConsumer consumer : consumers) {
            consumer.consume(logEntry)
        }
    }

    void consume() {
        for (final entry : sourceConsumerMap.entrySet()) {
            entry.value.consume(entry.key)
        }
        close()
    }

    @Override
    Iterator<LogEntry> iterator() {
        if (sourceConsumerMap.isEmpty()) {
            throw new RuntimeException('No sources defined to iterate thru')
        }
        return new LogEntryIterator()
    }

//    Stream<LogEntry> stream() {
//        throw new UnsupportedOperationException()
////        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), 0), false)
//    }


    private static LogConsumer lookupWrapped(LogConsumer consumer) {
        if (consumer instanceof AsynchronousJobs.AsynchronousJobConsumer) {
            consumer = ((AsynchronousJobs.AsynchronousJobConsumer) consumer).consumer
        }
        if (consumer instanceof LineReaderConsumer) {
            consumer = ((LineReaderConsumer) consumer).sourceConsumer
        }
        return consumer
    }

    private class LogEntryIterator implements Iterator<LogEntry> {
        private Queue<Tuple> sources = new LinkedList<>()

        private LineReader lineReader
        private LogConsumer sourceConsumer
        private String sourceName
        private LogEntry lastEntry
        private int currentRow
        private int nEntries

        LogEntryIterator() {
            for (Map.Entry<LogEntry, LogConsumer> entry : sourceConsumerMap.entrySet()) {
                LogConsumer sourceConsumer = lookupWrapped(entry.value)
                sources.add(new Tuple(LineReader.toLineReader(entry.key), LineReader.getSourceName(entry.key), sourceConsumer))
            }
        }

        protected void nextSource() {
            lastEntry = null
            currentRow = nEntries = 0
            if (sources.size()) {
                final tuple = sources.poll()
                lineReader = tuple.get(0) as LineReader
                sourceName = tuple.get(1)
                sourceConsumer = tuple.get(2) as LogConsumer
                log.info("Consuming '$sourceName'")
                doNext(true)
            }
        }

        @Override
        boolean hasNext() {
            if (processContext && lastEntry == null && sources.size() > 0) {
                nextSource()
            }
            boolean result = processContext && lastEntry != null
            if (!result) {
                LogCrawler.this.close()
            }
            return result
        }

        protected LogEntry doNext(boolean sourceOpen = false) {
            LogEntry result = null
            String line = null
            while (result == null && (line = lineReader.readLine()) != null) {
                final logEntry = new LogEntry(sourceName, line, ++currentRow)
                sourceConsumer.consume(logEntry)
                if (logEntry.isEmpty()) {
                    if (multiline && lastEntry) {
                        lastEntry.line += LineReader.LINE_BREAK + line
                    }
                } else {
                    nEntries++
                    consume(lastEntry, sourceConsumer)
                    result = sourceOpen ? logEntry : lastEntry
                    lastEntry = logEntry
                }
            }
            if (result == null) {
                consume(lastEntry, sourceConsumer)
                result = lastEntry
                if (line == null) {
                    log.info("Done consuming '$sourceName'. Processed '$currentRow' rows, consumed '$nEntries' entries")
                    Utils.closeQuietly(lineReader)
                    lastEntry = null
                }
            }
            return result
        }

        @Override
        LogEntry next() {
            return doNext()
        }
    }

    protected static void checkDelegateClosure(Closure closure, def builder) {
        if (closure) {
            closure.setDelegate(builder)
            closure.setResolveStrategy(Closure.DELEGATE_ONLY)
            closure.call()
        }
    }

    static LogCrawlerBuilder builder(String... args) {
        return new LogCrawlerBuilder().init(args)
    }

    private static void usage() {
        println """
Crawls files/folder based on logging consumer rules

Usage:
logCrawler [$LogCrawlerBuilder.LOG4J_ARG/$LogCrawlerBuilder.REGEX_ARG option] [sourceFiles/sourceFolders...] [<builder script>-log.groovy]

WHERE:
    [$LogCrawlerBuilder.LOG4J_ARG/$LogCrawlerBuilder.REGEX_ARG option]
        Logging configuration option; option is required before file/folder definition.
        '$LogCrawlerBuilder.LOG4J_ARG option' can be either a log4j config file (applied ONLY for folder) OR a log4j conversion pattern.
        '$LogCrawlerBuilder.REGEX_ARG option' regular expression string used to match logging entry line
    [<builder script>-log.groovy]
        Logging crawler builder Groovy configuration script.
"""
    }

    static void main(String[] args) {
        try {
            final startTime = System.currentTimeMillis()
            builder(args).build().consume()
            log.info("Crawled using: '${args.join(', ')}'; Execution time: ${DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true)}")
        } catch (IllegalArgumentException | UnsupportedOperationException ignore) {
            System.err.println(ignore)
            usage()
        }
    }

}
