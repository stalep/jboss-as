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
package org.jboss.as.cli;

import java.io.File;
import java.util.Collection;

import org.jboss.as.cli.aesh.ConnectionContext;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.controller.client.ModelControllerClient;


/**
 *
 * @author Alexey Loubyansky
 */
public interface CommandContext extends ConnectionContext {

    /**
     * Returns the current command's arguments as a string.
     * @return current command's arguments as a string or null if the command was entered w/o arguments.
     */
    String getArgumentsString();

    /**
     * Parsed command line arguments.
     * @return  parsed command line arguments.
     */
    ParsedCommandLine getParsedCommandLine();

    /**
     * Prints a string to the CLI's output.
     * @param message the message to print
     */
    void printLine(String message);

    /**
     * Prints a collection of strings as columns to the CLI's output.
     * @param col  the collection of strings to print as columns.
     */
    void printColumns(Collection<String> col);

    /**
     * Clears the screen.
     */
    void clearScreen();

    /**
     * Bind the controller to an existing, connected client.
     */
    void bindClient(ModelControllerClient newClient);

    /**
     * Returns the default host the controller client will be connected to.
     *
     * @deprecated Use {@link CommandContext#getDefaultControllerAddress()} instead.
     *
     * @return  the default host the controller client will be connected to.
     */
    @Deprecated
    String getDefaultControllerHost();

    /**
     * Returns the default port the controller client will be connected to.
     *
     * @deprecated Use {@link CommandContext#getDefaultControllerAddress()} instead.
     *
     * @return  the default port the controller client will be connected to.
     */
    @Deprecated
    int getDefaultControllerPort();

    /**
     * Returns the current operation request parser.
     * @return  current operation request parser.
     */
    CommandLineParser getCommandLineParser();

    /**
     * Returns the current prefix.
     * @return current prefix
     */
    OperationRequestAddress getCurrentNodePath();

    void setCurrentNodePath(OperationRequestAddress address);

    /**
     * Returns the prefix formatter.
     * @return the prefix formatter.
     */
    NodePathFormatter getNodePathFormatter();

    /**
     * Returns the provider of operation request candidates for tab-completion.
     * @return provider of operation request candidates for tab-completion.
     */
    OperationCandidatesProvider getOperationCandidatesProvider();

    /**
     * Returns the history of all the commands and operations.
     * @return  the history of all the commands and operations.
     */
    CommandHistory getHistory();

    /**
     * Returns the default command line completer.
     * @return  the default command line completer.
     */
    CommandLineCompleter getDefaultCommandCompleter();

    /**
     * Adds a listener for CLI events.
     * @param listener  the listener
     */
    void addEventListener(CliEventListener listener);

    /**
     * This method will start an interactive session.
     * It requires an initialized at the construction time console.
     */
    void interact();

    /**
     * Returns current default filesystem directory.
     * @return  current default filesystem directory.
     */
    File getCurrentDir();

    /**
     * Changes the current default filesystem directory to the argument.
     * @param dir  the new default directory
     */
    void setCurrentDir(File dir);

    /**
     * Whether the info or error messages should be written to the terminal output.
     *
     * The output of the info and error messages is done in the following way:
     * 1) the message is always logged using a logger
     *    (which is disabled in the config by default);
     * 2) if the output target was specified on the command line using '>'
     *    it would be used;
     * 3) if the output target was not specified, whether the message is
     *    written or not to the terminal output will depend on
     *    whether it's a silent mode or not.
     *
     * @return  true if the CLI is in the silent mode, i.e. not writing info
     *          and error messages to the terminal output, otherwise - false.
     */
    boolean isSilent();

    /**
     * Enables of disables the silent mode.
     *
     * @param silent  true if the CLI should go into the silent mode,
     *                false if the CLI should resume writing info
     *                and error messages to the terminal output.
     */
    void setSilent(boolean silent);

    /**
     * Returns the current terminal window width in case the console
     * has been initialized. Otherwise -1.
     *
     * @return  current terminal with if the console has been initialized,
     *          -1 otherwise
     */
    int getTerminalWidth();

    /**
     * Returns the current terminal window height in case the console
     * has been initialized. Otherwise -1.
     *
     * @return  current terminal height if the console has been initialized,
     *          -1 otherwise
     */
    int getTerminalHeight();

    /**
     * Initializes a variable with the given name with the given value.
     * The name of the variable must follow the rules for Java identifiers
     * but not contain '$' character.
     * If the variable already exists, its value will be silently overridden.
     * Passing in null as the value will remove the variable altogether.
     * If the variable with the given name has not been defined and the value
     * passed in is null, the method will return silently.
     *
     * @param name  name of the variable
     * @param value  value for the variable
     * @throws CommandLineException  in case the name contains illegal characters
     */
    void setVariable(String name, String value) throws CommandLineException;

    /**
     * Returns the value for the variable. If the variable has not been defined
     * the method will return null.
     *
     * @param name  name of the variable
     * @return  the value of the variable or null if the variable has not been
     *          defined
     */
    String getVariable(String name);

    /**
     * Returns a collection of all the defined variable names.
     * If there no variables defined, an empty collection will be returned.
     *
     * @return  collection of all the defined variable names or
     *          an empty collection if no variables has been defined
     */
    Collection<String> getVariables();
}
