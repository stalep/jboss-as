/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.aesh.console.ConsoleCallback;
import org.jboss.aesh.console.ConsoleOutput;
import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliConnection;
import org.jboss.as.cli.CliEvent;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandCompleter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.OperationCommand;
import org.jboss.as.cli.SSLConfig;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.batch.impl.DefaultBatchManager;
import org.jboss.as.cli.batch.impl.DefaultBatchedCommand;
import org.jboss.as.cli.callback.ConsoleLoginCallback;
import org.jboss.as.cli.handlers.ArchiveHandler;
import org.jboss.as.cli.handlers.ClearScreenHandler;
import org.jboss.as.cli.handlers.CommandCommandHandler;
import org.jboss.as.cli.handlers.ConnectHandler;
import org.jboss.as.cli.handlers.DeployHandler;
import org.jboss.as.cli.handlers.DeploymentInfoHandler;
import org.jboss.as.cli.handlers.DeploymentOverlayHandler;
import org.jboss.as.cli.handlers.EchoDMRHandler;
import org.jboss.as.cli.handlers.GenericTypeOperationHandler;
import org.jboss.as.cli.handlers.HelpHandler;
import org.jboss.as.cli.handlers.HistoryHandler;
import org.jboss.as.cli.handlers.LsHandler;
import org.jboss.as.cli.handlers.OperationRequestHandler;
import org.jboss.as.cli.handlers.PrefixHandler;
import org.jboss.as.cli.handlers.PrintWorkingNodeHandler;
import org.jboss.as.cli.handlers.QuitHandler;
import org.jboss.as.cli.handlers.ReadAttributeHandler;
import org.jboss.as.cli.handlers.ReadOperationHandler;
import org.jboss.as.cli.handlers.ReloadHandler;
import org.jboss.as.cli.handlers.ShutdownHandler;
import org.jboss.as.cli.handlers.UndeployHandler;
import org.jboss.as.cli.handlers.VersionHandler;
import org.jboss.as.cli.handlers.batch.BatchClearHandler;
import org.jboss.as.cli.handlers.batch.BatchDiscardHandler;
import org.jboss.as.cli.handlers.batch.BatchEditLineHandler;
import org.jboss.as.cli.handlers.batch.BatchHandler;
import org.jboss.as.cli.handlers.batch.BatchHoldbackHandler;
import org.jboss.as.cli.handlers.batch.BatchListHandler;
import org.jboss.as.cli.handlers.batch.BatchMoveLineHandler;
import org.jboss.as.cli.handlers.batch.BatchRemoveLineHandler;
import org.jboss.as.cli.handlers.batch.BatchRunHandler;
import org.jboss.as.cli.handlers.ifelse.ElseHandler;
import org.jboss.as.cli.handlers.ifelse.EndIfHandler;
import org.jboss.as.cli.handlers.ifelse.IfHandler;
import org.jboss.as.cli.handlers.jca.JDBCDriverNameProvider;
import org.jboss.as.cli.handlers.jca.JDBCDriverInfoHandler;
import org.jboss.as.cli.handlers.jca.XADataSourceAddCompositeHandler;
import org.jboss.as.cli.handlers.jms.CreateJmsResourceHandler;
import org.jboss.as.cli.handlers.jms.DeleteJmsResourceHandler;
import org.jboss.as.cli.handlers.module.ASModuleHandler;
import org.jboss.as.cli.handlers.trycatch.CatchHandler;
import org.jboss.as.cli.handlers.trycatch.EndTryHandler;
import org.jboss.as.cli.handlers.trycatch.FinallyHandler;
import org.jboss.as.cli.handlers.trycatch.TryHandler;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationCandidatesProvider;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestParser;
import org.jboss.as.cli.operation.impl.DefaultPrefixFormatter;
import org.jboss.as.cli.operation.impl.RolloutPlanCompleter;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.aesh.console.settings.Settings;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

/**
 *
 * @author Alexey Loubyansky
 */
class CommandContextImpl implements CommandContext, ModelControllerClientFactory.ConnectionCloseHandler {

    private static final Logger log = Logger.getLogger(CommandContext.class);

    /** the cli configuration */
    private final CliConfig config;

    private final CommandRegistry cmdRegistry = new CommandRegistry();

    private Console console;

    /** whether the session should be terminated */
    private boolean terminate;

    /** current command line */
    private String cmdLine;
    /** parsed command arguments */
    private DefaultCallbackHandler parsedCmd = new DefaultCallbackHandler(true);

    /** domain or standalone mode */
    private boolean domainMode;
    /** the controller client */
    private ModelControllerClient client;
    /** the default controller protocol */
    //private String defaultControllerProtocol;
    /** the default controller host */
    //private String defaultControllerHost;
    /** the default controller port */
    //private int defaultControllerPort;
    /** the host of the controller */
    //private String controllerHost;
    /** the port of the controller */
    //private int controllerPort = -1;
    /** the command line specified username */
    //private String username;
    /** the command line specified password */
    //private char[] password;
    /** the time to connect to a controller */
    //private final int connectionTimeout;
    /** The SSLContext when managed by the CLI */
    //private SSLContext sslContext;
    /** The TrustManager in use by the SSLContext, a reference is kept to rejected certificates can be captured. */
    //private LazyDelagatingTrustManager trustManager;
    /** various key/value pairs */
    private Map<String, Object> map = new HashMap<String, Object>();
    /** operation request address prefix */
    private final OperationRequestAddress prefix = new DefaultOperationRequestAddress();
    /** the prefix formatter */
    private final NodePathFormatter prefixFormatter = new DefaultPrefixFormatter();
    /** provider of operation request candidates for tab-completion */
    private final OperationCandidatesProvider operationCandidatesProvider;
    /** operation request handler */
    private final OperationRequestHandler operationHandler;
    /** batches */
    private BatchManager batchManager = new DefaultBatchManager();
    /** the default command completer */
    private final CommandCompleter cmdCompleter;

    /** output target */
    private BufferedWriter outputTarget;

    private List<CliEventListener> listeners = new ArrayList<CliEventListener>();

    /** the value of this variable will be used as the exit code of the vm, it is reset by every command/operation executed */
    private int exitCode;

    private File currentDir = new File("");

    /** whether to resolve system properties passed in as values of operation parameters*/
    private boolean resolveParameterValues;

    /** whether to write messages to the terminal output */
    private boolean silent;

    private ConsoleCallback defaultConsoleCallback;

    private ConsoleLoginState loginState = ConsoleLoginState.DISCONNECTED;

    private CliConnection cliConnection;

    /**
     * Version mode - only used when --version is called from the command line.
     *
     * @throws CliInitializationException
     */
    CommandContextImpl() throws CliInitializationException {
        this.console = null;
        this.operationCandidatesProvider = null;
        this.cmdCompleter = null;
        operationHandler = new OperationRequestHandler();
        initCommands();
        config = CliConfigImpl.load(this);

        initCliConnection(null, null, config.getDefaultControllerHost(), config.getConnectionTimeout(),
                config.getDefaultControllerPort(), config.getDefaultControllerProtocol(), config.getSslConfig());

        silent = config.isSilent();
    }

    CommandContextImpl(String username, char[] password) throws CliInitializationException {
        this(null, null, -1, username, password, false, -1);
    }

    /**
     * Default constructor used for both interactive and non-interactive mode.
     *
     */
    CommandContextImpl(String defaultControllerProtocol, String defaultControllerHost, int defaultControllerPort, String username, char[] password, boolean initConsole, final int connectionTimeout)
            throws CliInitializationException {

        config = CliConfigImpl.load(this);

        operationHandler = new OperationRequestHandler();

        initCliConnection(username, password, defaultControllerHost, config.getConnectionTimeout(),
                defaultControllerPort, defaultControllerProtocol, config.getSslConfig());
        /*
        this.username = username;
        this.password = password;
        this.connectionTimeout = connectionTimeout != -1 ? connectionTimeout : config.getConnectionTimeout();

        if (defaultControllerHost != null) {
            this.defaultControllerHost = defaultControllerHost;
        } else {
            this.defaultControllerHost = config.getDefaultControllerHost();
        }
        if (defaultControllerPort != -1) {
            this.defaultControllerPort = defaultControllerPort;
        } else {
            this.defaultControllerPort = config.getDefaultControllerPort();
        }
        if(defaultControllerProtocol != null) {
            this.defaultControllerProtocol = defaultControllerProtocol;
        } else {
            this.defaultControllerProtocol = config.getDefaultControllerProtocol();
        }
        */

        resolveParameterValues = config.isResolveParameterValues();
        silent = config.isSilent();
        initCommands();

        if (initConsole) {
            cmdCompleter = new CommandCompleter(cmdRegistry);
            initBasicConsole(null, null);
            console.addCompleter(cmdCompleter);
            this.operationCandidatesProvider = new DefaultOperationCandidatesProvider();
        } else {
            this.cmdCompleter = null;
            this.operationCandidatesProvider = null;
        }
    }

    CommandContextImpl(String defaultControllerHost, int defaultControllerPort,
            String username, char[] password,
            InputStream consoleInput, OutputStream consoleOutput)
            throws CliInitializationException {

        config = CliConfigImpl.load(this);

        operationHandler = new OperationRequestHandler();

        initCliConnection(username, password, defaultControllerHost, config.getConnectionTimeout(),
                defaultControllerPort, config.getDefaultControllerProtocol(), config.getSslConfig());

        /*
        this.username = username;
        this.password = password;
        this.connectionTimeout = config.getConnectionTimeout();

        if (defaultControllerHost != null) {
            this.defaultControllerHost = defaultControllerHost;
        } else {
            this.defaultControllerHost = config.getDefaultControllerHost();
        }
        if (defaultControllerPort != -1) {
            this.defaultControllerPort = defaultControllerPort;
        } else {
            this.defaultControllerPort = config.getDefaultControllerPort();
        }

        this.defaultControllerProtocol = config.getDefaultControllerProtocol();
        */

        resolveParameterValues = config.isResolveParameterValues();
        silent = config.isSilent();
        initCommands();

        cmdCompleter = new CommandCompleter(cmdRegistry);
        initBasicConsole(consoleInput, consoleOutput);
        console.addCompleter(cmdCompleter);
        this.operationCandidatesProvider = new DefaultOperationCandidatesProvider();
    }

    private void initCliConnection(String username, char[] password, String host, int timeout, int port,
                                   String protocol, SSLConfig sslConfig) throws CliInitializationException {
        cliConnection = new ConsoleConnection(username,
                password != null ? new String(password) : "",
                host != null ? host : config.getDefaultControllerHost(),
                protocol != null ? protocol : config.getDefaultControllerProtocol(),
                port != -1 ? port : config.getDefaultControllerPort(),
                timeout != -1 ? timeout : config.getConnectionTimeout(), sslConfig);
        cliConnection.setCommandContext(this);
    }

    protected void initBasicConsole(InputStream consoleInput, OutputStream consoleOutput) throws CliInitializationException {
        copyConfigSettingsToConsole(consoleInput, consoleOutput);

        if(isTerminated())
            return;
        else if(cmdCompleter == null) {
            throw new IllegalStateException("The console hasn't been initialized at construction time.");
        }
        else if (client == null) {
            printLine("You are disconnected at the moment. Type 'connect' to connect to the server or"
                    + " 'help' for the list of supported commands.");
        }
        defaultConsoleCallback = new ConsoleCallback() {
            @Override
            public int readConsoleOutput(ConsoleOutput output) throws IOException {
                if (output.getBuffer() == null) {
                    terminateSession();
                } else {
                    if(cliConnection.getState() == ConsoleLoginState.CONNECTED ||
                            cliConnection.getState() == ConsoleLoginState.DISCONNECTED) {
                        handleSafe(output.getBuffer().trim());
                    }
                    else if(cliConnection.getState() == ConsoleLoginState.USERNAME) {
                        cliConnection.setUsername( output.getBuffer().trim());
                        cliConnection.setState( ConsoleLoginState.PASSWORD);
                    }
                    else if(cliConnection.getState() == ConsoleLoginState.PASSWORD) {
                        cliConnection.setPassword( output.getBuffer().trim());
                        cliConnection.setState(ConsoleLoginState.DISCONNECTED);
                        try {
                            connectController();
                        } catch (CommandLineException e) {
                            console.print(e.getMessage());
                            console.printNewLine();
                            log.error("Failed to connect: ",e);
                        }
                        //try to connect, based on return. set proper state
                    }
                    console.setPrompt(getPrompt());
                }
                return 0;
            }
        };
        this.console = Console.Factory.getConsole(this, defaultConsoleCallback);
        this.console.start();
        console.setPrompt(getPrompt());
    }

    private void copyConfigSettingsToConsole(InputStream consoleInput, OutputStream consoleOutput) {
        if(consoleInput != null)
            Settings.getInstance().setInputStream(consoleInput);
        if(consoleOutput != null)
            Settings.getInstance().setStdOut(consoleOutput);

        Settings.getInstance().setLogging(true);
        Settings.getInstance().setHistoryDisabled(!config.isHistoryEnabled());
        Settings.getInstance().setHistoryFile(new File(config.getHistoryFileDir(), config.getHistoryFileName()));
        Settings.getInstance().setHistorySize(config.getHistoryMaxSize());
        //Settings.getInstance().setEnablePipelineAndRedirectionParser(false);
    }

    private void initCommands() {
        cmdRegistry.registerHandler(new PrefixHandler(), "cd", "cn");
        cmdRegistry.registerHandler(new ClearScreenHandler(), "clear", "cls");
        cmdRegistry.registerHandler(new CommandCommandHandler(cmdRegistry), "command");
        cmdRegistry.registerHandler(new ConnectHandler(), "connect");
        cmdRegistry.registerHandler(new EchoDMRHandler(), "echo-dmr");
        cmdRegistry.registerHandler(new HelpHandler(cmdRegistry), "help", "h");
        cmdRegistry.registerHandler(new HistoryHandler(), "history");
        cmdRegistry.registerHandler(new LsHandler(), "ls");
        cmdRegistry.registerHandler(new ASModuleHandler(this), "module");
        cmdRegistry.registerHandler(new PrintWorkingNodeHandler(), "pwd", "pwn");
        cmdRegistry.registerHandler(new QuitHandler(), "quit", "q", "exit");
        cmdRegistry.registerHandler(new ReadAttributeHandler(this), "read-attribute");
        cmdRegistry.registerHandler(new ReadOperationHandler(this), "read-operation");
        cmdRegistry.registerHandler(new ReloadHandler(this), "reload");
        cmdRegistry.registerHandler(new ShutdownHandler(this), "shutdown");
        cmdRegistry.registerHandler(new VersionHandler(), "version");

        // deployment
        cmdRegistry.registerHandler(new DeployHandler(this), "deploy");
        cmdRegistry.registerHandler(new UndeployHandler(this), "undeploy");
        cmdRegistry.registerHandler(new DeploymentInfoHandler(this), "deployment-info");
        cmdRegistry.registerHandler(new DeploymentOverlayHandler(this), "deployment-overlay");

        // batch commands
        cmdRegistry.registerHandler(new BatchHandler(this), "batch");
        cmdRegistry.registerHandler(new BatchDiscardHandler(), "discard-batch");
        cmdRegistry.registerHandler(new BatchListHandler(), "list-batch");
        cmdRegistry.registerHandler(new BatchHoldbackHandler(), "holdback-batch");
        cmdRegistry.registerHandler(new BatchRunHandler(this), "run-batch");
        cmdRegistry.registerHandler(new BatchClearHandler(), "clear-batch");
        cmdRegistry.registerHandler(new BatchRemoveLineHandler(), "remove-batch-line");
        cmdRegistry.registerHandler(new BatchMoveLineHandler(), "move-batch-line");
        cmdRegistry.registerHandler(new BatchEditLineHandler(), "edit-batch-line");

        // try-catch
        cmdRegistry.registerHandler(new TryHandler(), "try");
        cmdRegistry.registerHandler(new CatchHandler(), "catch");
        cmdRegistry.registerHandler(new FinallyHandler(), "finally");
        cmdRegistry.registerHandler(new EndTryHandler(), "end-try");

        // if else
        cmdRegistry.registerHandler(new IfHandler(), "if");
        cmdRegistry.registerHandler(new ElseHandler(), "else");
        cmdRegistry.registerHandler(new EndIfHandler(), "end-if");

        // data-source
        GenericTypeOperationHandler dsHandler = new GenericTypeOperationHandler(this, "/subsystem=datasources/data-source", null);
        final DefaultCompleter driverNameCompleter = new DefaultCompleter(JDBCDriverNameProvider.INSTANCE);
        dsHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        cmdRegistry.registerHandler(dsHandler, "data-source");
        GenericTypeOperationHandler xaDsHandler = new GenericTypeOperationHandler(this, "/subsystem=datasources/xa-data-source", null);
        xaDsHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        // override the add operation with the handler that accepts xa props
        final XADataSourceAddCompositeHandler xaDsAddHandler = new XADataSourceAddCompositeHandler(this, "/subsystem=datasources/xa-data-source");
        xaDsAddHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        xaDsHandler.addHandler("add", xaDsAddHandler);
        cmdRegistry.registerHandler(xaDsHandler, "xa-data-source");
        cmdRegistry.registerHandler(new JDBCDriverInfoHandler(this), "jdbc-driver-info");

        // JMS
        cmdRegistry.registerHandler(new GenericTypeOperationHandler(this, "/subsystem=messaging/hornetq-server=default/jms-queue", "queue-address"), "jms-queue");
        cmdRegistry.registerHandler(new GenericTypeOperationHandler(this, "/subsystem=messaging/hornetq-server=default/jms-topic", "topic-address"), "jms-topic");
        cmdRegistry.registerHandler(new GenericTypeOperationHandler(this, "/subsystem=messaging/hornetq-server=default/connection-factory", null), "connection-factory");
        // these are used for the cts setup
        cmdRegistry.registerHandler(new CreateJmsResourceHandler(this), false, "create-jms-resource");
        cmdRegistry.registerHandler(new DeleteJmsResourceHandler(this), false, "delete-jms-resource");

        // rollout plan
        final GenericTypeOperationHandler rolloutPlan = new GenericTypeOperationHandler(this, "/management-client-content=rollout-plans/rollout-plan", null);
        rolloutPlan.addValueConverter("content", HeadersArgumentValueConverter.INSTANCE);
        rolloutPlan.addValueCompleter("content", RolloutPlanCompleter.INSTANCE);
        cmdRegistry.registerHandler(rolloutPlan, "rollout-plan");

        // supported but hidden from tab-completion until stable implementation
        cmdRegistry.registerHandler(new ArchiveHandler(this), false, "archive");
    }

    public int getExitCode() {
        return exitCode;
    }

    @Override
    public boolean isTerminated() {
        return terminate;
    }

    private StringBuilder lineBuffer;

    @Override
    public void handle(String line) throws CommandLineException {
        if (line.isEmpty() || line.charAt(0) == '#') {
            return; // ignore comments
        }

        int i = line.length() - 1;
        while(i > 0 && line.charAt(i) <= ' ') {
            if(line.charAt(--i) == '\\') {
                break;
            }
        }
        if(line.charAt(i) == '\\') {
            if(lineBuffer == null) {
                lineBuffer = new StringBuilder();
            }
            lineBuffer.append(line, 0, i);
            lineBuffer.append(' ');
            return;
        } else if(lineBuffer != null) {
            lineBuffer.append(line);
            line = lineBuffer.toString();
            lineBuffer = null;
        }

        resetArgs(line);
        try {
            if (parsedCmd.getFormat() == OperationFormat.INSTANCE) {
                final ModelNode request = parsedCmd.toOperationRequest(this);

                if (isBatchMode()) {
                    StringBuilder op = new StringBuilder();
                    op.append(getNodePathFormatter().format(parsedCmd.getAddress()));
                    op.append(line.substring(line.indexOf(':')));
                    DefaultBatchedCommand batchedCmd = new DefaultBatchedCommand(op.toString(), request);
                    Batch batch = getBatchManager().getActiveBatch();
                    batch.add(batchedCmd);
                    printLine("#" + batch.size() + " " + batchedCmd.getCommand());
                } else {
                    set("OP_REQ", request);
                    try {
                        operationHandler.handle(this);
                    } finally {
                        set("OP_REQ", null);
                    }
                }
            } else {
                final String cmdName = parsedCmd.getOperationName();
                CommandHandler handler = cmdRegistry.getCommandHandler(cmdName.toLowerCase());
                if (handler != null) {
                    if (isBatchMode() && handler.isBatchMode(this)) {
                        if (!(handler instanceof OperationCommand)) {
                            throw new CommandLineException("The command is not allowed in a batch.");
                        } else {
                            try {
                                ModelNode request = ((OperationCommand) handler).buildRequest(this);
                                BatchedCommand batchedCmd = new DefaultBatchedCommand(line, request);
                                Batch batch = getBatchManager().getActiveBatch();
                                batch.add(batchedCmd);
                                printLine("#" + batch.size() + " " + batchedCmd.getCommand());
                            } catch (CommandFormatException e) {
                                throw new CommandFormatException("Failed to add to batch '" + line + "'", e);
                            }
                        }
                    } else {
                        handler.handle(this);
                    }
                } else {
                    throw new CommandLineException("Unexpected command '" + line + "'. Type 'help --commands' for the list of supported commands.");
                }
            }
        } finally {
            // so that getArgumentsString() doesn't return this line
            // during the tab-completion of the next command
            cmdLine = null;
        }
    }

    public void handleSafe(String line) {
        exitCode = 0;
        try {
            handle(line);
        } catch (CommandLineException e) {
            final StringBuilder buf = new StringBuilder();
            buf.append(e.getLocalizedMessage());
            Throwable t = e.getCause();
            while(t != null) {
                buf.append(": ").append(t.getLocalizedMessage());
                t = t.getCause();
            }
            error(buf.toString());
        }
    }

    @Override
    public String getArgumentsString() {
        // a little hack to support tab-completion of commands and ops spread across multiple lines
        if(lineBuffer != null) {
            return lineBuffer.toString();
        }
        if (cmdLine != null && parsedCmd.getOperationName() != null) {
            int cmdNameLength = parsedCmd.getOperationName().length();
            if (cmdLine.length() == cmdNameLength) {
                return null;
            } else {
                return cmdLine.substring(cmdNameLength + 1);
            }
        }
        return null;
    }

    @Override
    public synchronized void terminateSession() {
        if(console != null) {
            console.stop();
        }
        if(!terminate) {
            disconnectController();
            terminate = true;
        }
    }

    @Override
    public void printLine(String message) {
        final Level logLevel;
        if(exitCode != 0) {
            logLevel = Level.ERROR;
        } else {
            logLevel = Level.INFO;
        }
        if(log.isEnabled(logLevel)) {
            log.log(logLevel, message);
        }

        if (outputTarget != null) {
            try {
                outputTarget.append(message);
                outputTarget.newLine();
                outputTarget.flush();
            } catch (IOException e) {
                System.err.println("Failed to print '" + message + "' to the output target: " + e.getLocalizedMessage());
            }
            return;
        }

        if(!silent) {
            if (console != null) {
                console.print(message);
                console.printNewLine();
            } else { // non-interactive mode
                System.out.println(message);
            }
        }
    }

    protected void error(String message) {
        this.exitCode = 1;
        printLine(message);
    }

    public void setConsoleLoginState(ConsoleLoginState state) {
        loginState = state;
    }

    @Override
    public void printColumns(Collection<String> col) {
        if(log.isInfoEnabled()) {
            log.info(col);
        }
        if (outputTarget != null) {
            try {
                for (String item : col) {
                    outputTarget.append(item);
                    outputTarget.newLine();
                }
            } catch (IOException e) {
                System.err.println("Failed to print columns '" + col + "' to the console: " + e.getLocalizedMessage());
            }
            return;
        }

        if(!silent) {
            if (console != null) {
                console.printColumns(col);
            } else { // non interactive mode
                for (String item : col) {
                    System.out.println(item);
                }
            }
        }
    }

    @Override
    public void set(String key, Object value) {
        map.put(key, value);
    }

    @Override
    public Object get(String key) {
        return map.get(key);
    }

    @Override
    public Object remove(String key) {
        return map.remove(key);
    }

    @Override
    public ModelControllerClient getModelControllerClient() {
        return client;
    }

    @Override
    public CommandLineParser getCommandLineParser() {
        return DefaultOperationRequestParser.INSTANCE;
    }

    @Override
    public OperationRequestAddress getCurrentNodePath() {
        return prefix;
    }

    @Override
    public NodePathFormatter getNodePathFormatter() {

        return prefixFormatter;
    }

    @Override
    public OperationCandidatesProvider getOperationCandidatesProvider() {
        return operationCandidatesProvider;
    }

    @Override
    public void connectController() throws CommandLineException {
        connectController(null, null, -1);
    }

    @Override
    public void connectController(String protocol, String host, int port) throws CommandLineException {
        if (host != null) {
            cliConnection.setHost(host);
        }

        if (port > 0) {
            cliConnection.setPort(port);
        }

        if(protocol != null) {
            cliConnection.setProtocol(protocol);
        }

        /*
        CliConnection cliConnection = new ConsoleConnection(null, null, host, protocol, port, connectionTimeout,
                this, null, trustManager, sslContext);
                */
        //console.setCallback( new ConsoleLoginCallback(console, cliConnection));
        new ConsoleLoginCallback(console, cliConnection);
    }

    @Override
    public void bindClient(ModelControllerClient newClient) {
        bindClient(newClient, null, -1);
    }

    @Override
    public void bindClient(ModelControllerClient newClient, String host, int port) {
        if (newClient != null) {
            if (this.client != null) {
                disconnectController();
            }

            log.info("setting client to: "+newClient+", this means we're connected");
            client = newClient;
            cliConnection.setHost(host);
            cliConnection.setPort(port);

            List<String> nodeTypes = Util.getNodeTypes(newClient, new DefaultOperationRequestAddress());
            domainMode = nodeTypes.contains(Util.SERVER_GROUP);
        }
        //make sure the default callback is used
        if(console != null)
            console.setCallback(defaultConsoleCallback);
    }

    @Override
    public File getCurrentDir() {
        return currentDir;
    }

    @Override
    public void setCurrentDir(File dir) {
        if(dir == null) {
            throw new IllegalArgumentException("dir is null");
        }
        this.currentDir = dir;
    }

    @Override
    public void disconnectController() {
        if (this.client != null) {
            StreamUtils.safeClose(client);
            // if(loggingEnabled) {
            // printLine("Closed connection to " + this.controllerHost + ':' +
            // this.controllerPort);
            // }
            client = null;
            //this.controllerHost = null;
            //this.controllerPort = -1;
            domainMode = false;
            notifyListeners(CliEvent.DISCONNECTED);
        }
        promptConnectPart = null;
    }

    @Override
    public String getControllerHost() {
        return cliConnection.getHost();
    }

    @Override
    public int getControllerPort() {
        return cliConnection.getPort();
    }

    @Override
    public void clearScreen() {
        if(console != null) {
            console.clearScreen();
        }
    }

    String promptConnectPart;

    String getPrompt() {
        if(lineBuffer != null) {
            return "> ";
        }
        if(cliConnection.getState() == ConsoleLoginState.USERNAME) {
            return "Username: ";
        }
        if(cliConnection.getState() == ConsoleLoginState.PASSWORD) {
            return "Password: ";
        }
        if(cliConnection.getState() == ConsoleLoginState.SERTIFICATE) {
            //should not happen, ConsoleLoginCallback should be set as the
            //active callback when state == SERTIFICATE
        }
        StringBuilder buffer = new StringBuilder();
        if (promptConnectPart == null) {
            buffer.append('[');
            if (cliConnection.getHost() != null && cliConnection.getState() == ConsoleLoginState.CONNECTED) {
                if (domainMode) {
                    buffer.append("domain@");
                } else {
                    buffer.append("standalone@");
                }
                buffer.append(cliConnection.getHost()).append(':').append(cliConnection.getPort()).append(' ');
                promptConnectPart = buffer.toString();
            }
            else if(cliConnection.getState() == ConsoleLoginState.DISCONNECTED) {
                buffer.append("disconnected ");
            }
        }
        else {
            buffer.append(promptConnectPart);
        }

        if (prefix.isEmpty()) {
            buffer.append('/');
        } else {
            buffer.append(prefix.getNodeType());
            final String nodeName = prefix.getNodeName();
            if (nodeName != null) {
                buffer.append('=').append(nodeName);
            }
        }

        if (isBatchMode()) {
            buffer.append(" #");
        }
        buffer.append("] ");
        return buffer.toString();
    }

    @Override
    public CommandHistory getHistory() {
        if(console == null) {
            try {
                initBasicConsole(null, null);
            } catch (CliInitializationException e) {
                throw new IllegalStateException("Failed to initialize console.", e);
            }
        }
        return console.getHistory();
    }

    @Override
    public String getDefaultControllerHost() {
        return cliConnection.getHost();
    }

    @Override
    public int getDefaultControllerPort() {
        return cliConnection.getPort();
    }

    private void resetArgs(String cmdLine) throws CommandFormatException {
        if (cmdLine != null) {
            parsedCmd.parse(prefix, cmdLine);
            setOutputTarget(parsedCmd.getOutputTarget());
        }
        this.cmdLine = cmdLine;
    }

    @Override
    public boolean isBatchMode() {
        return batchManager.isBatchActive();
    }

    @Override
    public BatchManager getBatchManager() {
        return batchManager;
    }

    @Override
    public BatchedCommand toBatchedCommand(String line) throws CommandFormatException {
        return new DefaultBatchedCommand(line, buildRequest(line, true));
    }

    @Override
    public ModelNode buildRequest(String line) throws CommandFormatException {
        return buildRequest(line, false);
    }

    protected ModelNode buildRequest(String line, boolean batchMode) throws CommandFormatException {

        if (line == null || line.isEmpty()) {
            throw new OperationFormatException("The line is null or empty.");
        }

        final DefaultCallbackHandler originalParsedArguments = this.parsedCmd;
        try {
            this.parsedCmd = new DefaultCallbackHandler();
            resetArgs(line);

            if (parsedCmd.getFormat() == OperationFormat.INSTANCE) {
                final ModelNode request = this.parsedCmd.toOperationRequest(this);
                StringBuilder op = new StringBuilder();
                op.append(prefixFormatter.format(parsedCmd.getAddress()));
                op.append(line.substring(line.indexOf(':')));
                return request;
            }

            final CommandHandler handler = cmdRegistry.getCommandHandler(parsedCmd.getOperationName());
            if (handler == null) {
                throw new OperationFormatException("No command handler for '" + parsedCmd.getOperationName() + "'.");
            }
            if(batchMode) {
                if(!handler.isBatchMode(this)) {
                    throw new OperationFormatException("The command is not allowed in a batch.");
                }
            } else if (!(handler instanceof OperationCommand)) {
                throw new OperationFormatException("The command does not translate to an operation request.");
            }

            return ((OperationCommand) handler).buildRequest(this);
        } finally {
            this.parsedCmd = originalParsedArguments;
        }
    }

    @Override
    public CommandLineCompleter getDefaultCommandCompleter() {
        return cmdCompleter;
    }

    @Override
    public ParsedCommandLine getParsedCommandLine() {
        return parsedCmd;
    }

    @Override
    public boolean isDomainMode() {
        return domainMode;
    }

    @Override
    public void addEventListener(CliEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null.");
        }
        listeners.add(listener);
    }

    @Override
    public CliConfig getConfig() {
        return config;
    }

    protected void setOutputTarget(String filePath) {
        if (filePath == null) {
            this.outputTarget = null;
            return;
        }
        FileWriter writer;
        try {
            writer = new FileWriter(filePath, false);
        } catch (IOException e) {
            error(e.getLocalizedMessage());
            return;
        }
        this.outputTarget = new BufferedWriter(writer);
    }

    protected void notifyListeners(CliEvent event) {
        for (CliEventListener listener : listeners) {
            listener.cliEvent(event, this);
        }
    }

    @Override
    public void interact() {
        //doesnt do much atm
    }

    @Override
    public boolean isResolveParameterValues() {
        return resolveParameterValues;
    }

    @Override
    public void setResolveParameterValues(boolean resolve) {
        this.resolveParameterValues = resolve;
    }

    @Override
    public void handleClose() {
        if(parsedCmd.getFormat().equals(OperationFormat.INSTANCE) && "shutdown".equals(parsedCmd.getOperationName())) {
            final String restart = parsedCmd.getPropertyValue("restart");
            if(restart == null || !Util.TRUE.equals(restart)) {
                disconnectController();
                printLine("");
                printLine("The connection to the controller has been closed as the result of the shutdown operation.");
                printLine("(Although the command prompt will wrongly indicate connection until the next line is entered)");
            }
        }
    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }

    @Override
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Override
    public int getTerminalWidth() {
        if(console == null) {
            try {
                this.initBasicConsole(null, null);
            } catch (CliInitializationException e) {
                this.error("Failed to initialize the console: " + e.getLocalizedMessage());
                return 80;
            }
        }
        return console.getTerminalWidth();
    }

    @Override
    public int getTerminalHeight() {
        if(console == null) {
            try {
                this.initBasicConsole(null, null);
            } catch (CliInitializationException e) {
                this.error("Failed to initialize the console: " + e.getLocalizedMessage());
                return 24;
            }
        }
        return console.getTerminalHeight();
    }


    @Override
    public void setCliConnection(CliConnection cliConnection) {
        this.cliConnection = cliConnection;
    }

}
