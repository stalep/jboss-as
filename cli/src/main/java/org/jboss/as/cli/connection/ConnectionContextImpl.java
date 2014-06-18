/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.connection;

import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.ControllerAddressResolver;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.batch.impl.DefaultBatchManager;
import org.jboss.as.cli.batch.impl.DefaultBatchedCommand;
import org.jboss.as.cli.handlers.OperationRequestHandler;
import org.jboss.as.cli.impl.CliConfigImpl;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultPrefixFormatter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.GeneralTimeoutHandler;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ConnectionContext keep the connection context
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ConnectionContextImpl implements ConnectionContext {

    /** the cli configuration */
    private final CliConfig config;
    private final ControllerAddressResolver addressResolver;

    /** domain or standalone mode */
    private boolean domainMode;
    /** the controller client */
    private ModelControllerClient client;

    /** the address of the current controller */
    private ControllerAddress currentAddress;
    /** the command line specified username */
    private final String username;
    /** the command line specified password */
    private final String password;
    /** flag to disable the local authentication mechanism */
    private final boolean disableLocalAuth;
    /** the time to connect to a controller */
    private int connectionTimeout;
    /** The SSLContext when managed by the CLI */
    private CliSSLContext sslContext;
    /** various key/value pairs */
    private Map<String, Object> map = new HashMap<String, Object>();
    /** operation request address prefix */
    private final OperationRequestAddress prefix = new DefaultOperationRequestAddress();
    /** the prefix formatter */
    private final NodePathFormatter prefixFormatter = DefaultPrefixFormatter.INSTANCE;
     /** whether the session should be terminated */
    private boolean terminate;
    /** the timeout handler */
    private final GeneralTimeoutHandler timeoutHandler = new GeneralTimeoutHandler();

      /** operation request handler */
    private final OperationRequestHandler operationHandler;

    /** batches */
    private BatchManager batchManager = new DefaultBatchManager();

      private List<CliEventListener> listeners = new ArrayList<CliEventListener>();

    /** the value of this variable will be used as the exit code of the vm, it is reset by every command/operation executed */
    private int exitCode;

    private File currentDir = new File("");

    /** whether to resolve system properties passed in as values of operation parameters*/
    private boolean resolveParameterValues;

    /** whether to write messages to the terminal output */
    private boolean silent;

    private static final Logger log = Logger.getLogger(ConnectionContextImpl.class);

    public ConnectionContextImpl() throws CliInitializationException {
        this.config = CliConfigImpl.load(null);
        addressResolver = ControllerAddressResolver.newInstance(config, null);
        username = null;
        password = null;
        operationHandler = new OperationRequestHandler();
        disableLocalAuth = false;
        resolveParameterValues = config.isResolveParameterValues();
        this.connectionTimeout = config.getConnectionTimeout();
    }

    public ConnectionContextImpl(String username, String password, boolean disableLocalAuth) throws CliInitializationException {
        this(null, username, password, disableLocalAuth, false, -1);
    }

    public ConnectionContextImpl(String defaultController, String username, String password, boolean disableLocalAuth,
                                 boolean initConsole, final int connectionTimeout) throws CliInitializationException {

        config = CliConfigImpl.load(null);
        addressResolver = ControllerAddressResolver.newInstance(config, defaultController);
                operationHandler = new OperationRequestHandler();

        this.username = username;
        this.password = password;
        this.disableLocalAuth = disableLocalAuth;
        this.connectionTimeout = connectionTimeout != -1 ? connectionTimeout : config.getConnectionTimeout();

        resolveParameterValues = config.isResolveParameterValues();
        silent = config.isSilent();
        this.connectionTimeout = connectionTimeout != -1 ? connectionTimeout : config.getConnectionTimeout();

        sslContext = new CliSSLContext(config.getSslConfig(), timeoutHandler);
    }

    @Override
    public CliConfig getConfig() {
        return config;
    }

    @Override
    public void terminateSession() {
        terminate = true;
        disconnectController();
    }

    @Override
    public boolean isTerminated() {
        return terminate;
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
    public void connectController() throws CommandLineException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void connectController(String controller) throws CommandLineException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void connectController(String host, int port) throws CommandLineException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void bindClient(ModelControllerClient newClient, ControllerAddress address) {
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
    public void disconnectController() {
        if (this.client != null) {
            StreamUtils.safeClose(client);
            client = null;
            this.currentAddress = null;
            domainMode = false;
        }
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
    public OperationRequestAddress getCurrentNodePath() {
        return prefix;
    }

    @Override
    public NodePathFormatter getNodePathFormatter() {
        return prefixFormatter;
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

        /*
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
        */
        return null;
    }


    @Override
    public boolean isDomainMode() {
        return domainMode;
    }

    @Override
    public int getExitCode() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handle(String line) throws CommandLineException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleSafe(String line) {
        //To change body of implemented methods use File | Settings | File Templates.
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
    public boolean isResolveParameterValues() {
        return resolveParameterValues;
    }

    @Override
    public void setResolveParameterValues(boolean resolve) {
        resolveParameterValues = resolve;
    }

    @Override
    public boolean isSilent() {
        return silent;
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
    public void handleClose() {
        disconnectController();
        /*
        if(parsedCmd.getFormat().equals(OperationFormat.INSTANCE) && "shutdown".equals(parsedCmd.getOperationName())) {
            final String restart = parsedCmd.getPropertyValue("restart");
            if(restart == null || !Util.TRUE.equals(restart)) {
                disconnectController();
                printLine("");
                printLine("The connection to the controller has been closed as the result of the shutdown operation.");
                printLine("(Although the command prompt will wrongly indicate connection until the next line is entered)");
            } // else maybe still notify the listeners that the connection has been closed
        }
        */
    }

}
