package org.prismus.scrambler.log

import groovy.transform.CompileStatic

@CompileStatic
class SampleExtension {

    String rest(String host, int port) {
        return 'connected'
    }

}
