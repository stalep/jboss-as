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


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;


/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 * @author Alexey Loubyansky <olubyans@redhat.com>
 */
public class CLIWrapper {

    private CommandContext ctx;

    private ByteArrayOutputStream consoleOut;
    private String username = Authentication.USERNAME;
    private String password = Authentication.PASSWORD;

    private static class CLIWrapperHolder {
        static final CLIWrapper INSTANCE = new CLIWrapper();
    }

    public static CLIWrapper getInstance() {
        return CLIWrapperHolder.INSTANCE;
    }

    /**
     * Creates new CLI wrapper.
     */
    private CLIWrapper() {
    }

    public synchronized void init(InputStream consoleInput) throws CliInitializationException {
        if(ctx == null) {
            consoleOut = new ByteArrayOutputStream();
            final char[] password = getPassword() == null ? null : getPassword().toCharArray();
            System.setProperty("aesh.terminal","org.jboss.aesh.terminal.TestTerminal");
            ctx = CLITestUtil.getCommandContext(
                    TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), getUsername(), password,
                    consoleInput, consoleOut);
        }
    }

    /**
     *  Connect to the server using <code>connect</code> command.
     *
     * @param cliAddress The default name of the property containing the cli address. If null the value of the {@code node0} property is
     * used, and if that is absent {@code localhost} is used
     */
    public synchronized void connect(String cliAddress) throws CliInitializationException {
        if(ctx == null)
            init(null);
        Assert.assertTrue(sendConnect(cliAddress));
    }

    public boolean isConnected() {
        return ctx.getModelControllerClient() != null;
    }

    public CommandContext getCommandContext() {
        return ctx;
    }

    /**
     * Sends a line with the connect command. This will look for the {@code node0} system property
     * and use that as the address. If the system property is not set {@code localhost} will
     * be used
     */
    public boolean sendConnect() {
        return sendConnect(null);
    }

    /**
     * Sends a line with the connect command.
     * @param cliAddress The address to connect to. If null it will look for the {@code node0} system
     * property and use that as the address. If the system property is not set {@code localhost} will
     * be used
     */
    public synchronized boolean sendConnect(String cliAddress) {
        if(ctx != null ) {
            //if we're already connected return true
            if(ctx.getModelControllerClient() != null)
                return true;
            String addr = cliAddress != null ? cliAddress : TestSuiteEnvironment.getServerAddress();
            try {
                ctx.connectController("http-remoting", addr, TestSuiteEnvironment.getServerPort());
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return true;
            } catch (CommandLineException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    /**
     * Sends command line to CLI.
     *
     * @param line specifies the command line.
     * @param readEcho if set to true reads the echo response form the CLI.
     * @throws Exception
     */
    public boolean sendLine(String line, boolean ignoreError)  {
        consoleOut.reset();
        if(ignoreError) {
            ctx.handleSafe(line);
            return ctx.getExitCode() == 0;
        } else {
            try {
                ctx.handle(line);
            } catch (CommandLineException e) {
                Assert.fail("Failed to execute line '" + line + "': " + e.getLocalizedMessage());
            }
        }
        return true;
    }

    /**
     * Sends command line to CLI.
     *
     * @param line specifies the command line.
     * @throws Exception
     */
    public void sendLine(String line) {
        sendLine(line, false);
    }

    /**
     * Reads the last command's output.
     *
     * @return next line from CLI output
     */
    public String readOutput()  {
        if(consoleOut.size() <= 0) {
            return null;
        }
        return new String(consoleOut.toByteArray());
    }

    public ByteArrayOutputStream getConsoleOut() {
        return consoleOut;
    }

    /**
     * Consumes all available output from CLI and converts the output to ModelNode operation format
     *
     * @return array of CLI output lines
     */
    public CLIOpResult readAllAsOpResult() throws IOException {
        final String response = readOutput();
        if(response == null) {
            return new CLIOpResult();
        }
        final ModelNode node = ModelNode.fromString(response);
        return new CLIOpResult(node);
    }

    /**
     * Sends quit command to CLI.
     *
     * @throws Exception
     */
    public synchronized void quit() {
        if(ctx != null) {
            ctx.terminateSession();
            ctx = null;
        }
    }

    /**
     * Returns CLI status.
     *
     * @return true if and only if the CLI has finished.
     */
    public boolean hasQuit() {
        return ctx == null || ctx.isTerminated();
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
