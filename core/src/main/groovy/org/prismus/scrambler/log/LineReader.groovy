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

/**
 * @author Serge Pruteanu
 */
abstract class LineReader implements Closeable {
    static final String LINE_BREAK = System.getProperty('line.separator')
    abstract String readLine()

    @SuppressWarnings("GroovyAssignabilityCheck")
    static LineReader toLineReader(LogEntry entry) {
        return toLineReader(entry.source)
    }

    @CompileStatic
    static LineReader toLineReader(RandomAccessFile rf) {
        return new RandomAccessFileLineReader(rf)
    }

    @CompileStatic
    static List<RandomAccessFileLineReader> partitionReaders(String filePath) {
        return partitionReaders(filePath, 1)
    }

    @CompileStatic
    private static RandomAccessFile createRaf(String filePath) {
        return new RandomAccessFile(filePath, 'r')
    }

    @CompileStatic
    static List<RandomAccessFileLineReader> partitionReaders(String filePath, long nParts) {
        final List<RandomAccessFileLineReader> results = new ArrayList<>()
        RandomAccessFile raf = createRaf(filePath)
        long endPos = raf.length()
        long step = endPos / nParts as long
        while (nParts--) {
            raf = createRaf(filePath)
            results.add(new RandomAccessFileLineReader(raf, endPos))
            long startPos = endPos - step
            if (startPos) {
                raf.seek(startPos)
                raf.readLine()
                endPos = raf.filePointer
            }
        }
        return results
    }

    @CompileStatic
    static LineReader toLineReader(InputStream inputStream) {
        return new IoLineReader(new BufferedReader(new InputStreamReader(inputStream)))
    }

    @CompileStatic
    static LineReader toLineReader(Reader reader) {
        return new IoLineReader(reader instanceof BufferedReader ? reader : new BufferedReader(reader))
    }

    @CompileStatic
    static LineReader toLineReader(File file) {
        return new IoLineReader(new BufferedReader(new FileReader(file)))
    }

    @CompileStatic
    static LineReader toLineReader(String content) {
        return new IoLineReader(new StringReader(content))
    }

    @CompileStatic
    static LogEntry newLogSource(Object source, String sourceName) {
        return addSourceName(new LogEntry(source: source), sourceName)
    }

    @CompileStatic
    static LogEntry addSourceName(LogEntry entry, String sourceName) {
        entry.put('SourceName', sourceName)
        return entry
    }

    @CompileStatic
    static String getSourceName(LogEntry entry) {
        return entry.get('SourceName')
    }

    @CompileStatic
    private static class IoLineReader extends LineReader {
        final Reader reader

        IoLineReader(Reader reader) {
            this.reader = reader
        }

        @Override
        String readLine() {
            return reader.readLine()
        }

        @Override
        void close() throws IOException {
            reader.close()
        }
    }

    @CompileStatic
    private static class RandomAccessFileLineReader extends LineReader {
        final RandomAccessFile raf
        final long endPosition

        RandomAccessFileLineReader(RandomAccessFile raf) {
            this(raf, raf.length())
        }

        RandomAccessFileLineReader(RandomAccessFile raf, long endPosition) {
            this.raf = raf
            this.endPosition = endPosition
        }

        @Override
        String readLine() {
            final filePointer = raf.getFilePointer()
            return filePointer < endPosition ? raf.readLine() : null
        }

        @Override
        void close() throws IOException {
            raf.close()
        }
    }

}
