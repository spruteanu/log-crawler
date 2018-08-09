package org.prismus.scrambler.log

import groovy.transform.CompileStatic

@CompileStatic
class DummyConsumer implements LogConsumer {
    String name

    @Override
    void consume(LogEntry entry) {
        entry.put('dummy', name)
    }

    @CompileStatic
    static class Builder extends ConsumerBuilder<DummyConsumer> {
        String name

        void empty(String name) {
            this.name = name
        }

        @Override
        protected DummyConsumer build() {
            return new DummyConsumer(name: name)
        }
    }

}
