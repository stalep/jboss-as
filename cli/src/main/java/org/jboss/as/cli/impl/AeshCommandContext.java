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

import org.jboss.as.cli.CliConfig;
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
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.ControllerAddressResolver;
import org.jboss.as.cli.OperationCommand;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.aesh.connection.CliSSLContext;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.batch.impl.DefaultBatchManager;
import org.jboss.as.cli.batch.impl.DefaultBatchedCommand;
import org.jboss.as.cli.handlers.OperationRequestHandler;
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
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Loubyansky
 */
class AeshCommandContext implements CommandContext, ModelControllerClientFactory.ConnectionCloseHandler {

    private static final Logger log = Logger.getLogger(CommandContext.class);

    /** the cli configuration */
    private final CliConfig config;
    private final ControllerAddressResolver addressResolver;

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

    /** the address of the current controller */
    private ControllerAddress currentAddress;
    /** the command line specified username */
    private final String username;
    /** the command line specified password */
    private final char[] password;
    /** flag to disable the local authentication mechanism */
    private final boolean disableLocalAuth;
    /** the time to connect to a controller */
    private final int connectionTimeout;
    /** The SSLContext when managed by the CLI */
    private CliSSLContext sslContext;
    /** various key/value pairs */
    private Map<String, Object> map = new HashMap<String, Object>();
    /** operation request address prefix */
    private final OperationRequestAddress prefix = new DefaultOperationRequestAddress();
    /** the prefix formatter */
    private final NodePathFormatter prefixFormatter = DefaultPrefixFormatter.INSTANCE;
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

    private Map<String, String> variables;

    /**
     * Version mode - only used when --version is called from the command line.
     *
     * @throws org.jboss.as.cli.CliInitializationException
     */
    AeshCommandContext() throws CliInitializationException {
        this.console = null;
        this.operationCandidatesProvider = null;
        this.cmdCompleter = null;
        operationHandler = new OperationRequestHandler();
        config = CliConfigImpl.load(this);
        addressResolver = ControllerAddressResolver.newInstance(config, null);
        resolveParameterValues = config.isResolveParameterValues();
        this.connectionTimeout = config.getConnectionTimeout();
        silent = config.isSilent();
        username = null;
        password = null;
        disableLocalAuth = false;
        initSSLContext();
    }

    AeshCommandContext(String username, char[] password, boolean disableLocalAuth) throws CliInitializationException {
        this(null, username, password, disableLocalAuth, false, -1);
    }

    /**
     * Default constructor used for both interactive and non-interactive mode.
     *
     */
    AeshCommandContext(String defaultController, String username, char[] password, boolean disableLocalAuth, boolean initConsole, final int connectionTimeout)
            throws CliInitializationException {

        config = CliConfigImpl.load(this);
        addressResolver = ControllerAddressResolver.newInstance(config, defaultController);

        operationHandler = new OperationRequestHandler();

        this.username = username;
        this.password = password;
        this.disableLocalAuth = disableLocalAuth;
        this.connectionTimeout = connectionTimeout != -1 ? connectionTimeout : config.getConnectionTimeout();

        resolveParameterValues = config.isResolveParameterValues();
        silent = config.isSilent();

        initSSLContext();

        if (initConsole) {
            cmdCompleter = new CommandCompleter(cmdRegistry);
            //initBasicConsole(null, null);
            //console.addCompleter(cmdCompleter);
            this.operationCandidatesProvider = new DefaultOperationCandidatesProvider();
        } else {
            this.cmdCompleter = null;
            this.operationCandidatesProvider = null;
        }
    }

    AeshCommandContext(String defaultController,
                       String username, char[] password, boolean disableLocalAuth,
                       InputStream consoleInput, OutputStream consoleOutput)
            throws CliInitializationException {

        config = CliConfigImpl.load(this);
        addressResolver = ControllerAddressResolver.newInstance(config, defaultController);

        operationHandler = new OperationRequestHandler();

        this.username = username;
        this.password = password;
        this.disableLocalAuth = disableLocalAuth;
        this.connectionTimeout = config.getConnectionTimeout();

        resolveParameterValues = config.isResolveParameterValues();
        silent = config.isSilent();

        initSSLContext();

        cmdCompleter = new CommandCompleter(cmdRegistry);
        console.addCompleter(cmdCompleter);
        this.operationCandidatesProvider = new DefaultOperationCandidatesProvider();
    }


    public int getExitCode() {
        return exitCode;
    }

    /**
     * Initialise the SSLContext and associated TrustManager for this CommandContext.
     *
     * If no configuration is specified the default mode of operation will be to use a lazily initialised TrustManager with no
     * KeyManager.
     */
    private void initSSLContext() throws CliInitializationException {
        sslContext = new CliSSLContext(config.getSslConfig());
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
        } catch(CommandLineException e) {
            throw e;
        } catch(Throwable t) {
            throw new CommandLineException("Failed to handle '" + line + "'", t);
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
        } catch(Throwable t) {
            final StringBuilder buf = new StringBuilder();
            buf.append(t.getLocalizedMessage());
            Throwable t1 = t.getCause();
            while(t1 != null) {
                if(t1.getLocalizedMessage() != null) {
                    buf.append(": ").append(t1.getLocalizedMessage());
                } else {
                    t1.printStackTrace();
                }
                t1 = t1.getCause();
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
    public void terminateSession() {
        terminate = true;
        disconnectController();
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
        connectController(null);
    }

    @Override
    public void connectController(String controller) throws CommandLineException {

    }

    @Override
    @Deprecated
    public void connectController(String host, int port) throws CommandLineException {
        try {
            connectController(new URI(null, null, host, port, null, null, null).toString().substring(2));
        } catch (URISyntaxException e) {
            throw new CommandLineException("Unable to construct URI for connection.", e);
        }
    }

    @Override
    public void bindClient(ModelControllerClient newClient, ControllerAddress address) {
        initNewClient(newClient, address);
    }

    @Override
    public void bindClient(ModelControllerClient newClient) {
        initNewClient(newClient, null);
    }

    private void initNewClient(ModelControllerClient newClient, ControllerAddress address) {
        if (newClient != null) {
            if (this.client != null) {
                disconnectController();
            }

            client = newClient;
            this.currentAddress = address;

            List<String> nodeTypes = Util.getNodeTypes(newClient, new DefaultOperationRequestAddress());
            domainMode = nodeTypes.contains(Util.SERVER_GROUP);
        }
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
            this.currentAddress = null;
            domainMode = false;
            notifyListeners(CliEvent.DISCONNECTED);
        }
        promptConnectPart = null;
    }

    @Override
    @Deprecated
    public String getDefaultControllerHost() {
        return config.getDefaultControllerHost();
    }

    @Override
    @Deprecated
    public int getDefaultControllerPort() {
        return config.getDefaultControllerPort();
    }

    @Override
    public ControllerAddress getDefaultControllerAddress() {
        return config.getDefaultControllerAddress();
    }

    @Override
    public String getControllerHost() {
        return currentAddress != null ? currentAddress.getHost() : null;
    }

    @Override
    public int getControllerPort() {
        return currentAddress != null ? currentAddress.getPort() : -1;
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
        StringBuilder buffer = new StringBuilder();
        if (promptConnectPart == null) {
            buffer.append('[');
            String controllerHost = getControllerHost();
            if (controllerHost != null) {
                if (domainMode) {
                    buffer.append("domain@");
                } else {
                    buffer.append("standalone@");
                }
                buffer.append(controllerHost).append(':').append(getControllerPort()).append(' ');
                promptConnectPart = buffer.toString();
            } else {
                buffer.append("disconnected ");
            }
        } else {
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
        return console.getHistory();
    }

    private void resetArgs(String cmdLine) throws CommandFormatException {
        if (cmdLine != null) {
            parsedCmd.parse(prefix, cmdLine, this);
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
        if(cmdCompleter == null) {
            throw new IllegalStateException("The console hasn't been initialized at construction time.");
        }

        if (this.client == null) {
            printLine("You are disconnected at the moment. Type 'connect' to connect to the server or"
                    + " 'help' for the list of supported commands.");
        }

        try {
            while (!isTerminated()) {
                final String line = console.readLine(getPrompt());
                if (line == null) {
                    terminateSession();
                } else {
                    handleSafe(line.trim());
                }
            }
            printLine("");
        } catch (Throwable t) {
            t.printStackTrace();
        }
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
            } // else maybe still notify the listeners that the connection has been closed
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
    public ControllerAddressResolver getAddressResolver() {
        return addressResolver;
    }

    @Override
    public CliSSLContext getSSLContext() {
        return sslContext;
    }

    @Override
    public boolean doDisableLocalAuth() {
        return disableLocalAuth;
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public int getTerminalWidth() {
        return console.getTerminalWidth();
    }

    @Override
    public int getTerminalHeight() {
        return console.getTerminalHeight();
    }

    @Override
    public void setVariable(String name, String value) throws CommandLineException {
        if(name == null || name.isEmpty()) {
            throw new CommandLineException("Variable name can't be null or an empty string");
        }
        if(!Character.isJavaIdentifierStart(name.charAt(0))) {
            throw new CommandLineException("Variable name must be a valid Java identifier (and not contain '$'): '" + name + "'");
        }
        for(int i = 1; i < name.length(); ++i) {
            final char c = name.charAt(i);
            if(!Character.isJavaIdentifierPart(c) || c == '$') {
                throw new CommandLineException("Variable name must be a valid Java identifier (and not contain '$'): '" + name + "'");
            }
        }

        if(value == null) {
            if(variables == null) {
                return;
            }
            variables.remove(name);
        } else {
            if(variables == null) {
                variables = new HashMap<String,String>();
            }
            variables.put(name, value);
        }
    }

    @Override
    public String getVariable(String name) {
        return variables == null ? null : variables.get(name);
    }

    @Override
    public Collection<String> getVariables() {
        return variables == null ? Collections.<String>emptySet() : variables.keySet();
    }

}
