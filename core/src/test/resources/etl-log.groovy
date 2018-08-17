import org.prismus.scrambler.log.Log4jConsumer
import org.prismus.scrambler.log.LogEntry

import java.text.SimpleDateFormat

/**
 * @author Serge Pruteanu
 */
log4j {
//    path 'D:/work/tm/bugs/176217/188947/logs/07-10'
//    configuration 'D:\\work\\tm\\bugs\\176217\\188947\\TMLogger.cfg'
    message {
        final message = get('Message')
        final category = get(Log4jConsumer.EVENT_CATEGORY)
        if (category && category.startsWith('com.edifecs.shared.filestore')) {
            match 'Message', ~/(?<Action>.*)(?:FileID|file)[: =\)]{1,}\s*(?<FileID>\w+)(?<Execution>.+)\s+(?<ExecutionTime>\d+)\s+ms/, {
                if (!get('Action')) {
                    put 'Action', get('Execution')
                }
                remove 'Execution'
            }
        } else
        if (message.endsWith('second(s).')) {
            match 'Message', ~/.+\s+(?<Action>\w+\s+to process the file)\s+(?<PackageID>.+)\s+\((?<TransmissionSID>\d+)\).+(?<Hours>\d+) hour\(s\)\s+(?<Minutes>\d+) minute\(s\)\s+(?<Seconds>.+) second\(s\)./
            put 'ExecutionTime', putFloat('Hours') * 3600 + putFloat('Minutes') * 60 + putFloat('Seconds')
        } else
        if (message.startsWith('Move failed package')) {
            put 'Action', 'Failed move package'
            match 'Message', ~/.+ZIP_(?<PackageID>.+) to Failed Queue./
        } else
        if (message.startsWith('Subcomponent')) {
            put 'Action', 'Failed processing'
            match 'Message', ~/.+\s+(?<PackageID>.+)\s+\((?<TransmissionSID>\d+)\)/
        } else
        if (message.startsWith('Created Transmission')) {
            put 'Action', 'Created Transmission'
            match 'Message', ~/.+:\s+(?<TransmissionSID>\d+)\s+for ETL package: (?:ID:|ZIP_)(?<PackageID>.*)/
        } else
        if (match('Message', ~/.+started to process the transmission (?<PackageID>.+)\s+\((?<TransmissionSID>\d+)\)/)) {
            put 'Action', 'Started transmission processing'
        } else
        if (message.contains('ID:')) {
            match 'Message', ~/(?<Action>.+)ID:(?<PackageID>.+)/
        } else
        {
            put 'Action', 'undetected'
        }
    }
}

//parallel()

toCsv "elt-log-${new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date())}.csv",
        Log4jConsumer.DATE, 'Action', 'TransmissionSID', 'PackageID', 'FileID', 'ExecutionTime', Log4jConsumer.THREAD_NAME, LogEntry.SOURCE_INFO, Log4jConsumer.MESSAGE
