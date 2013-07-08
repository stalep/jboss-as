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
package org.jboss.as.test.integration.management.util;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliConnection;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class CLITestUtil {

    private static final String JBOSS_CLI_CONFIG = "jboss.cli.config";
    private static final String AESH_TERMINAL = "aesh.terminal";
    private static final String AESH_TEST_TERMINAL = "org.jboss.aesh.terminal.TestTerminal";

    private static final String serverAddr = TestSuiteEnvironment.getServerAddress();
    private static final int serverPort = TestSuiteEnvironment.getServerPort();

    public static CommandContext getCommandContext() throws CliInitializationException {
        setJBossCliConfig();
        return new CommandContextDelegate(
                CommandContextFactory.getInstance().newCommandContext("http-remoting", serverAddr, serverPort, null, null));
    }

    public static CommandContext getCommandContext(String address, int port, String user, char[] pwd, InputStream in, OutputStream out)
            throws CliInitializationException {
        setJBossCliConfig();
        return new CommandContextDelegate(
                CommandContextFactory.getInstance().newCommandContext(address, port, user, pwd, in, out));
    }

    public static CommandContext getCommandContext(OutputStream out) throws CliInitializationException {
        SecurityActions.setSystemProperty(AESH_TERMINAL, AESH_TEST_TERMINAL);
        setJBossCliConfig();
        return new CommandContextDelegate(
                CommandContextFactory.getInstance().newCommandContext(serverAddr, serverPort, null, null, null, out));
    }

    protected static void setJBossCliConfig() {
        final String jbossCliConfig = SecurityActions.getSystemProperty(JBOSS_CLI_CONFIG);
        if(jbossCliConfig == null) {
            final String jbossDist = System.getProperty("jboss.dist");
            if(jbossDist == null) {
                fail("jboss.dist system property is not set");
            }
            SecurityActions.setSystemProperty(JBOSS_CLI_CONFIG, jbossDist + File.separator + "bin" + File.separator + "jboss-cli.xml");
        }
    }

    static class CommandContextDelegate implements CommandContext {


        private CommandContext delegate;

        public CommandContextDelegate(CommandContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public CliConfig getConfig() {
            return delegate.getConfig();
        }

        @Override
        public String getArgumentsString() {
            return delegate.getArgumentsString();
        }

        @Override
        public ParsedCommandLine getParsedCommandLine() {
            return delegate.getParsedCommandLine();
        }

        @Override
        public void printLine(String message) {
            delegate.printLine(message);
        }

        @Override
        public void printColumns(Collection<String> col) {
            delegate.printColumns(col);
        }

        @Override
        public void clearScreen() {
            delegate.clearScreen();
        }

        @Override
        public void terminateSession() {
            delegate.terminateSession();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public void set(String key, Object value) {
            delegate.set(key, value);
        }

        @Override
        public Object get(String key) {
            return delegate.get(key);
        }

        @Override
        public Object remove(String key) {
            return delegate.remove(key);
        }

        @Override
        public ModelControllerClient getModelControllerClient() {
            return delegate.getModelControllerClient();
        }

        @Override
        public void connectController(String protocol, String host, int port) throws CommandLineException {
            delegate.connectController(protocol,host,port);
        }

        @Override
        public void bindClient(ModelControllerClient newClient) {
            delegate.bindClient(newClient);
        }

        @Override
        public void bindClient(ModelControllerClient newClient, String host, int port) {
            delegate.bindClient(newClient,host,port);
        }

        @Override
        public void connectController() throws CommandLineException {
            delegate.connectController();
            try {
                Thread.sleep(250);
                int count = 0;
                while(delegate.getModelControllerClient() == null && count < 10) {
                    count++;
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void disconnectController() {
            delegate.disconnectController();
        }

        @Override
        public String getDefaultControllerHost() {
            return delegate.getDefaultControllerHost();
        }

        @Override
        public int getDefaultControllerPort() {
            return delegate.getDefaultControllerPort();
        }

        @Override
        public String getControllerHost() {
            return delegate.getControllerHost();
        }

        @Override
        public int getControllerPort() {
            return delegate.getControllerPort();
        }

        @Override
        public CommandLineParser getCommandLineParser() {
            return delegate.getCommandLineParser();
        }

        @Override
        public OperationRequestAddress getCurrentNodePath() {
            return delegate.getCurrentNodePath();
        }

        @Override
        public NodePathFormatter getNodePathFormatter() {
            return delegate.getNodePathFormatter();
        }

        @Override
        public OperationCandidatesProvider getOperationCandidatesProvider() {
            return delegate.getOperationCandidatesProvider();
        }

        @Override
        public CommandHistory getHistory() {
            return delegate.getHistory();
        }

        @Override
        public boolean isBatchMode() {
            return delegate.isBatchMode();
        }

        @Override
        public BatchManager getBatchManager() {
            return delegate.getBatchManager();
        }

        @Override
        public BatchedCommand toBatchedCommand(String line) throws CommandFormatException {
            return delegate.toBatchedCommand(line);
        }

        @Override
        public ModelNode buildRequest(String line) throws CommandFormatException {
            return delegate.buildRequest(line);
        }

        @Override
        public CommandLineCompleter getDefaultCommandCompleter() {
            return delegate.getDefaultCommandCompleter();
        }

        @Override
        public boolean isDomainMode() {
            return delegate.isDomainMode();
        }

        @Override
        public void addEventListener(CliEventListener listener) {
            delegate.addEventListener(listener);
        }

        @Override
        public int getExitCode() {
            return delegate.getExitCode();
        }

        @Override
        public void handle(String line) throws CommandLineException {
            delegate.handle(line);
        }

        @Override
        public void handleSafe(String line) {
            delegate.handleSafe(line);
        }

        @Override
        public void interact() {
            delegate.interact();
        }

        @Override
        public File getCurrentDir() {
            return delegate.getCurrentDir();
        }

        @Override
        public void setCurrentDir(File dir) {
            delegate.setCurrentDir(dir);
        }

        @Override
        public boolean isResolveParameterValues() {
            return delegate.isResolveParameterValues();
        }

        @Override
        public void setResolveParameterValues(boolean resolve) {
            delegate.setResolveParameterValues(resolve);
        }

        @Override
        public boolean isSilent() {
            return delegate.isSilent();
        }

        @Override
        public void setSilent(boolean silent) {
            delegate.setSilent(silent);
        }

        @Override
        public int getTerminalWidth() {
            return delegate.getTerminalWidth();
        }

        @Override
        public int getTerminalHeight() {
            return delegate.getTerminalHeight();
        }

        @Override
        public void setCliConnection(CliConnection cliConnection) {
            delegate.setCliConnection(cliConnection);
        }
    }
}
