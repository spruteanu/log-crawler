package org.prismus.scrambler.log

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Log
import org.apache.commons.lang3.StringUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.context.ApplicationContext

import javax.sql.DataSource
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * @author Serge Pruteanu
 */
@CompileStatic
@Log
class LogCrawlerBuilder {
    protected static final String LOG4J_ARG = '-log4j'
    protected static final String REGEX_ARG = '-regex'
    static final Comparator<Path> CREATED_DT_COMPARATOR = { Path l, Path r ->
        final leftCreated = Files.readAttributes(l, BasicFileAttributes.class).creationTime()
        final rightCreated = Files.readAttributes(r, BasicFileAttributes.class).creationTime()
        return leftCreated.compareTo(rightCreated)
    } as Comparator<Path>

    ObjectProvider provider = new DefaultObjectProvider()

    private LogCrawler logCrawler
    private final Map<String, Object> sourceNameConsumerMap = [:]
    private final Map<LogEntry, Object> sourceMap = [:]
    private final List<ConsumerBuilder> builders = []

    private final Map<String, ConsumerBuilder> registeredConsumerMethods = Collections.synchronizedMap(new LinkedHashMap<String, ConsumerBuilder>())

    LogCrawlerBuilder() {
        logCrawler = new LogCrawler()
    }

    @PackageScope
    void buildSources() {
        final List<String> sourceNames = new ArrayList<>()
        final Map<Object, LogConsumer> consumers = [:]
        for (Map.Entry<LogEntry, Object> entry : sourceMap.entrySet()) {
            final value = entry.value
            LogConsumer source = null
            if (value instanceof ConsumerBuilder) {
                if (consumers.containsKey(value)) {
                    source = consumers.get(value)
                } else {
                    source = value.build()
                    consumers.put(value, source)
                }
            }
            if (source) {
                final logEntry = entry.key
                logCrawler.source(logEntry, new LineReaderConsumer(logCrawler, source))
                final sourceName = LineReader.getSourceName(logEntry)
                if (sourceName) {
                    sourceNames.add(sourceName)
                }
            }
        }
        for (Map.Entry<LogEntry, Object> entry : sourceMap.entrySet()) {
            final value = entry.value
            LogConsumer source = null
            if (value instanceof LogConsumer) {
                source = value as LogConsumer
            }
            if (source) {
                final logEntry = entry.key
                logCrawler.source(logEntry, new LineReaderConsumer(logCrawler, source))
                final sourceName = LineReader.getSourceName(logEntry)
                if (sourceName) {
                    sourceNames.add(sourceName)
                }
            }
        }
        int difference = StringUtils.indexOfDifference(sourceNames.toArray() as String[])
        if (difference > 0) {
            for (LogEntry logEntry : sourceMap.keySet()) {
                final sourceName = LineReader.getSourceName(logEntry)
                if (sourceName) {
                    int idx = indexOfLastFolderSeparator(sourceName)
                    difference = Math.min(difference, indexOfLastFolderSeparator(sourceName, difference))
                    LineReader.addSourceName(logEntry, sourceName.substring(Math.min(idx, difference)))
                }
            }
        }
    }

    protected static int indexOfLastFolderSeparator(String sourceName, int sidx = -1) {
        if (sidx < 0) {
            sidx = sourceName.length()
        }
        def ch = '\\'
        int idx = sourceName.lastIndexOf(ch, sidx)
        if (idx < 0) {
            ch = '/'
            idx = sourceName.lastIndexOf(ch, sidx)
        }
        return idx < 0 ? sidx : idx + 1
    }

    @PackageScope
    void buildConsumers() {
        for (ConsumerBuilder builder : builders) {
            final logConsumer = builder.build()
            if (logConsumer) {
                logCrawler.using(logConsumer)
            }
        }
    }

    LogConsumer getConsumer(Object consumerId, Object... args) {
        return provider.get(consumerId, args) as LogConsumer
    }

    LogCrawlerBuilder parallel(int awaitTimeout = 0, TimeUnit unit = TimeUnit.MILLISECONDS) {
        builders.add(AsynchronousJobs.builder(logCrawler).withExecutorService(awaitTimeout, unit))
        return this
    }

    LogCrawlerBuilder parallel(ExecutorService executorService, int awaitTimeout = 0, TimeUnit unit = TimeUnit.MILLISECONDS) {
        builders.add(AsynchronousJobs.builder(logCrawler).withExecutorService(executorService, awaitTimeout, unit))
        return this
    }

    LogCrawlerBuilder provider(ObjectProvider provider) {
        this.provider = provider
        return this
    }

    LogCrawlerBuilder provider(ApplicationContext context) {
        this.provider = SpringObjectProvider.of(context)
        return this
    }

    LogCrawlerBuilder provider(Class... contextClass) {
        this.provider = SpringObjectProvider.of(contextClass)
        return this
    }

    LogCrawlerBuilder provider(String... contextXml) {
        this.provider = SpringObjectProvider.of(contextXml)
        return this
    }

    LogCrawlerBuilder using(LogConsumer consumer) {
        builders.add(new ConsumerBuilder().using(consumer))
        return this
    }

    LogCrawlerBuilder using(@DelegatesTo(LogEntry) Closure logEntryClosure) {
        return using(new ClosureConsumer(logEntryClosure))
    }

    LogCrawlerBuilder filter(Predicate predicate, LogConsumer consumer, LogConsumer... consumers) {
        def cs = consumers ? ContainerConsumer.of(consumer).addAll(consumers) : consumer
        return using(new PredicateConsumer(predicate, cs))
    }

    LogCrawlerBuilder filter(@DelegatesTo(LogEntry) Closure<Boolean> predicate, @DelegatesTo(LogEntry) Closure consumer, @DelegatesTo(LogEntry) Closure... consumers) {
        def cs = consumers ? ContainerConsumer.of(consumer).addAll(consumers) : new ClosureConsumer(consumer)
        return filter(new ClosurePredicate(predicate), cs)
    }

    LogCrawlerBuilder date(SimpleDateFormat dateFormat, String group = DateConsumer.DATE) {
        return using(DateConsumer.of(dateFormat, group))
    }

    LogCrawlerBuilder date(String dateFormat, String group = DateConsumer.DATE) {
        return using(DateConsumer.of(dateFormat, group))
    }

    protected void addSource(LogEntry logEntry) {
        sourceMap.put(logEntry, null)
    }

    LogCrawlerBuilder source(RandomAccessFile rf, String sourceName = null) {
        addSource(LineReader.newLogSource(rf, sourceName))
        return this
    }

    LogCrawlerBuilder source(InputStream inputStream, String sourceName = null) {
        addSource(LineReader.newLogSource(inputStream, sourceName))
        return this
    }

    LogCrawlerBuilder source(Reader reader, String sourceName = null) {
        addSource(LineReader.newLogSource(reader, sourceName))
        return this
    }

    LogCrawlerBuilder source(File file, String sourceName = null) {
        addSource(LineReader.newLogSource(file, sourceName ?: file.path))
        return this
    }

    LogCrawlerBuilder source(String content, String sourceName = null) {
        addSource(LineReader.newLogSource(content, sourceName))
        return this
    }

    protected void source(ConsumerBuilder builder, String sourceName, File folder, String fileFilter, Comparator<Path> fileSorter) {
        final files = listFiles(folder, fileFilter, fileSorter)
        for (File file : files) {
            final logEntry = LineReader.newLogSource(new LogEntry(source: file), file.path)
            register(logEntry, builder, sourceName)
            register(logEntry, builder, fileFilter)
        }
    }

    LogCrawlerBuilder source(String path, LogConsumer consumer, Comparator<Path> fileSorter = CREATED_DT_COMPARATOR) {
        int idx = Utils.indexOfFileFilter(path)
        String fileFilter = null
        if (idx >= 0) {
            fileFilter = path.substring(idx, path.length())
            path = path.substring(0, idx)
        }
        fileFilter = Utils.defaultFolderFilter(path, fileFilter, '*')

        final folder = new File(path)
        final files = listFiles(folder, fileFilter, fileSorter)
        final builder = using(consumer)
        for (File file : files) {
            register(LineReader.newLogSource(new LogEntry(source: folder), file.path), builder)
        }
        return builder
    }

    protected void register(LogEntry sourceEntry, Object sourceConsumer, String sourceName = null) {
        sourceMap.put(sourceEntry, sourceConsumer)
        if (sourceName) {
            sourceNameConsumerMap.put(sourceName, sourceConsumer)
        }
    }

    protected LogCrawlerBuilder sourceConsumer(Object sourceConsumer, String sourceName = null) {
        for (LogEntry sourceEntry : sourceMap.keySet()) {
            final sc = sourceMap.get(sourceEntry)
            if (sc == null) {
                register(sourceEntry, sourceConsumer, sourceName)
            }
        }
        return this
    }

    RegexConsumer.Builder regex(Pattern pattern, @DelegatesTo(RegexConsumer.Builder) Closure closure = null) {
        final builder = new RegexConsumer.Builder(this, RegexConsumer.of(pattern))
        LogCrawler.checkDelegateClosure(closure, builder)
        if (builder.path) {
            source(builder, pattern.pattern(), new File(builder.path), builder.fileFilter, builder.fileSorter)
        } else {
            sourceConsumer(builder, pattern.pattern())
        }
        return builder
    }

    RegexConsumer.Builder regex(String regEx, int flags = 0,
                                @DelegatesTo(RegexConsumer.Builder) Closure closure = null) {
        final builder = new RegexConsumer.Builder(this, RegexConsumer.of(regEx, flags))
        LogCrawler.checkDelegateClosure(closure, builder)
        if (builder.path) {
            source(builder, regEx, new File(builder.path), builder.fileFilter, builder.fileSorter)
        } else {
            sourceConsumer(builder, regEx)
        }
        return builder
    }

    RegexConsumer.Builder regex(@DelegatesTo(RegexConsumer.Builder) Closure closure) {
        final builder = new RegexConsumer.Builder(this, new RegexConsumer())
        LogCrawler.checkDelegateClosure(closure, builder)
        if (!builder.pattern) {
            throw new IllegalArgumentException('Regex pattern must be defined, please set it in configuration')
        }
        if (builder.path) {
            source(builder, builder.pattern, new File(builder.path), builder.fileFilter, builder.fileSorter)
        } else {
            sourceConsumer(builder, builder.pattern)
        }
        return builder
    }

    Log4jConsumer.Builder log4j(String conversionPattern, @DelegatesTo(Log4jConsumer.Builder) Closure closure = null) {
        final builder = new Log4jConsumer.Builder(this, Log4jConsumer.of(conversionPattern))
        LogCrawler.checkDelegateClosure(closure, builder)
        if (builder.path) {
            source(builder, conversionPattern, new File(builder.path), builder.fileFilter, builder.fileSorter)
        } else {
            sourceConsumer(builder, conversionPattern)
        }
        return builder
    }

    protected static String fileFilterToRegex(String fileFilter) {
        String result = fileFilter.replaceAll('\\*', '.*')
        result = result.replaceAll('\\?', '.')
        result = result.replaceAll('\\\\', '\\\\')
        return result
    }

    protected static List<File> listFiles(File folder, String fileFilter = '*', Comparator<Path> fileSorter = LogCrawlerBuilder.CREATED_DT_COMPARATOR) {
        assert folder.exists() && folder.isDirectory(), "Folder: '$folder.path' doesn't exists"
        log.info("Scanning '$folder.path' for logging sources using: '$fileFilter' filter")
        final Pattern filePattern = ~/${fileFilterToRegex(fileFilter)}/
        final results = Files.find(Paths.get(folder.toURI()), 999,
                { Path p, BasicFileAttributes bfa -> bfa.isRegularFile() && filePattern.matcher(p.getFileName().toString()).matches() }, FileVisitOption.FOLLOW_LINKS
        ).sorted(fileSorter).map({ it.toFile() }).collect(Collectors.toList())
        if (results) {
            log.info("Found '${results.size()}' files in '$folder.path'")
        } else {
            log.info("No files found in '$folder.path' using '$fileFilter' filter")
        }
        return results
    }

    RegexConsumer.Builder regex(File folder, Pattern pattern,
                                String fileFilter = '*', Comparator<Path> fileSorter = CREATED_DT_COMPARATOR) {
        final builder = regex(pattern)
        source(builder, pattern.pattern(), folder, fileFilter, fileSorter)
        return builder
    }

    Log4jConsumer.Builder log4j(File folder, String conversionPattern,
                                String fileFilter = '*', Comparator<Path> fileSorter = CREATED_DT_COMPARATOR,
                                @DelegatesTo(Log4jConsumer.Builder) Closure closure = null) {
        final builder = log4j(conversionPattern, closure)
        source(builder, conversionPattern, folder, fileFilter, fileSorter)
        return builder
    }

    Log4jConsumer.Builder log4j(String folder, String conversionPattern,
                                Comparator<Path> fileSorter = CREATED_DT_COMPARATOR,
                                @DelegatesTo(Log4jConsumer.Builder) Closure closure = null) {
        final builder = log4j(conversionPattern, closure).path(folder) as Log4jConsumer.Builder
        source(builder, conversionPattern, new File(folder), builder.fileFilter, fileSorter)
        return builder
    }

    LogCrawlerBuilder log4jConfig(File folder, String log4jConfig, Comparator<Path> fileSorter = CREATED_DT_COMPARATOR,
                                  @DelegatesTo(Log4jConsumer.Builder) Closure closure = null) {
        final log4jConsumerProperties = Log4jConsumer.extractLog4jConsumerProperties(Utils.readResourceText(log4jConfig).readLines())
        if (log4jConsumerProperties.isEmpty()) {
            throw new IllegalArgumentException("Either empty or there are no file loggers defined in '$log4jConfig'")
        }
        final filterConversionMap = Log4jConsumer.toLog4jFileConversionPattern(log4jConsumerProperties)
        for (Map.Entry<String, String> entry : filterConversionMap.entrySet()) {
            log4j(folder, entry.value, entry.key, fileSorter)
        }
        for (Map.Entry<String, Map<String, String>> entry : log4jConsumerProperties.entrySet()) {
            final loggerName = entry.key
            final loggerProperties = entry.value
            final builder = log4jBuilder(loggerProperties.get(Log4jConsumer.APPENDER_CONVERSION_PATTERN_PROPERTY))
            if (builder) {
                LogCrawler.checkDelegateClosure(closure, builder)
                sourceNameConsumerMap.put(loggerName, builder)
            }
        }
        return this
    }

    LogCrawlerBuilder log4jConfig(String filePath, String log4jConfigPath, Comparator<Path> fileSorter = CREATED_DT_COMPARATOR,
                                  @DelegatesTo(Log4jConsumer.Builder) Closure closure = null) {
        return log4jConfig(new File(filePath), log4jConfigPath, fileSorter, closure)
    }

    LogCrawlerBuilder log4j(@DelegatesTo(Log4jConsumer.Builder) Closure closure) {
        final builder = new Log4jConsumer.Builder(this, new Log4jConsumer())
        LogCrawler.checkDelegateClosure(closure, builder)
        if (!builder.pattern) {
            throw new IllegalArgumentException('Either conversion pattern or log4j config file must be defined, please set it in configuration')
        }
        if (builder.path && new File(builder.pattern).exists()) {
            log4jConfig(builder.path, builder.pattern, builder.fileSorter, closure)
        } else {
            log4j(builder.pattern, closure)
        }
        return this
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T> T builder(String sourceName) {
        return sourceNameConsumerMap.get(sourceName) as T
    }

    RegexConsumer.Builder regexBuilder(String regex) {
        return builder(regex)
    }

    Log4jConsumer.Builder log4jBuilder(String sourceName) {
        return builder(sourceName)
    }

    TableBatchConsumer.Builder toCsv(DataSource dataSource, @DelegatesTo(CsvWriterConsumer.Builder) Closure closure = null) {
        final builder = new TableBatchConsumer.Builder(this, TableBatchConsumer.of(dataSource, 'LogEntry'))
        LogCrawler.checkDelegateClosure(closure, builder)
        builders.add(builder)
        return builder
    }

    LogCrawlerBuilder toCsv(Writer writer, String... columns) {
        return using(CsvWriterConsumer.of(writer, columns))
    }

    LogCrawlerBuilder toCsv(File writer, String... columns) {
        return using(CsvWriterConsumer.of(writer, columns))
    }

    CsvWriterConsumer.Builder toCsv(File file, @DelegatesTo(CsvWriterConsumer.Builder) Closure closure = null) {
        final builder = new CsvWriterConsumer.Builder(this, CsvWriterConsumer.of(file))
        LogCrawler.checkDelegateClosure(closure, builder)
        builders.add(builder)
        return builder
    }

    LogCrawlerBuilder toCsv(String writer, String... columns) {
        return using(CsvWriterConsumer.of(writer, columns))
    }

    CsvWriterConsumer.Builder toCsv(String filePath, @DelegatesTo(CsvWriterConsumer.Builder) Closure closure = null) {
        final builder = new CsvWriterConsumer.Builder(this, CsvWriterConsumer.of(filePath))
        LogCrawler.checkDelegateClosure(closure, builder)
        builders.add(builder)
        return builder
    }

    LogCrawler build() {
        buildSources()
        buildConsumers()
        sourceMap.clear()
        sourceNameConsumerMap.clear()
        builders.clear()
        return logCrawler
    }

    private List toMethodTuple(String[] args, List<List> configTuple, int currentIdx, File file) {
        if (configTuple.isEmpty()) {
            throw new IllegalArgumentException("Illegal arguments provided: '${args.join(', ')}'; source type is unknown")
        }
        int i = configTuple.size() - 1
        for (; i > 0 && (configTuple.get(i)[1] as int) > currentIdx; i--) { ; }
        final String configType = configTuple.get(i)[0] as String
        final int configIdx = configTuple.get(i)[1] as int
        final configValue = args[configIdx]
        final boolean configFile = new File(configValue).exists()

        List result = null
        if (file.isDirectory()) {
            switch (configType) {
                case LOG4J_ARG:
                    if (configFile) {
                        result = ['log4jConfig', [file, configValue] as Object[]]
                    } else {
                        result = ['log4j', [file, configValue] as Object[]]
                    }
                    break
                case REGEX_ARG:
                    result = ['regexSourceFolder', [file, configValue] as Object[]]
                    break
            }
        } else {
            final sourceEntry = LineReader.newLogSource(new LogEntry(source: file), file.path)
            switch (configType) {
                case LOG4J_ARG:
                    if (configFile) {
                        throw new UnsupportedOperationException("Unsupported log4j config file option: '$configValue' for a single file: '$file.path'. Only conversion pattern is supported here")
                    }
                    final builder = log4j(configValue)
                    result = ['register', [sourceEntry, builder, configValue] as Object[]]
                    break
                case REGEX_ARG:
                    result = ['register', [sourceEntry, regex(configValue), configValue] as Object[]]
                    break
            }
        }
        if (!result) {
            throw new IllegalArgumentException("Illegal arguments provided: '${args.join(', ')}'; source type is unknown")
        }
        return result
    }

    protected LogCrawlerBuilder init(String... args) {
        if (!args) {
            return this
        }
        final List<List> sourceMethodTuple = []
        final configTypes = [LOG4J_ARG, REGEX_ARG] as Set
        final List<List> configTuple = []
        final Collection<String> scripts = []
        final unknownArgs = []
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.endsWith('groovy')) {
                scripts.add(arg)
            } else {
                if (configTypes.contains(arg.toLowerCase())) {
                    if (args.length > i + 1) {
                        configTuple.add([arg, ++i])
                    } else {
                        unknownArgs.add("$arg argument must be followed by option and files to be applied")
                    }
                    continue
                }
                final file = new File(arg)
                if (file.exists()) {
                    sourceMethodTuple.add(toMethodTuple(args, configTuple, i, file))
                } else {
                    unknownArgs.add(arg)
                }
            }
        }
        if (sourceMethodTuple.isEmpty() && configTuple.size() > 0) {
            unknownArgs.add('No source configuration options provided')
        }
        if (unknownArgs) {
            throw new IllegalArgumentException("Unsupported/unknown arguments: '${unknownArgs.join(', ')}'; arguments: '${args.join(', ')}'")
        }
        for (final i = 0; i < sourceMethodTuple.size(); i++) {
            final tuple = sourceMethodTuple.get(i)
            InvokerHelper.invokeMethod(this, tuple[0].toString(), tuple[1])
        }
        for (final script : scripts) {
            initGroovyScriptBuilder(this, Utils.readGroovyResourceText(script))
        }
        return this
    }

    private void checkRegisteredMethodsLoaded() {
        if (registeredConsumerMethods.isEmpty()) {
            synchronized (registeredConsumerMethods) {
                final builders = provider.getRegisteredBuilders()
                for (final builder : builders) {
                    for (final method : builder.getClass().methods) {
                        registeredConsumerMethods.put(method.name, builder)
                    }
                }
            }
        }
    }

    def methodMissing(String name, args) {
        checkRegisteredMethodsLoaded()
        if (registeredConsumerMethods.containsKey(name)) {
            final consumerBuilder = registeredConsumerMethods.get(name)
            consumerBuilder.invokeMethod(name, args)
            builders.add(consumerBuilder)
        } else {
            throw new UnsupportedOperationException("There are no such method: $name defined with arguments: $args")
        }
    }

    private static GroovyShell checkCreateShell(Properties parserProperties = new Properties()) {
        final compilerConfiguration = (parserProperties != null && parserProperties.size() > 0) ? new CompilerConfiguration(parserProperties) : new CompilerConfiguration()
        compilerConfiguration.setScriptBaseClass(DelegatingScript.name)

        final importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports(LogCrawler.getPackage().name)
        compilerConfiguration.addCompilationCustomizers(importCustomizer)

        return new GroovyShell(compilerConfiguration)
    }

    protected static LogCrawlerBuilder initGroovyScriptBuilder(LogCrawlerBuilder builder, String definitionText) {
        final shell = checkCreateShell()
        final script = (DelegatingScript) shell.parse(definitionText)
        script.setDelegate(builder)
        script.run()
        return builder
    }

}
