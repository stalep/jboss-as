/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh;

import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.ControllerAddressResolver;
import org.jboss.as.cli.aesh.connection.CliSSLContext;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.impl.ModelControllerClientFactory;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

import java.io.File;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface ConnectionContext extends ModelControllerClientFactory.ConnectionCloseHandler {
    /**
     * Returns the JBoss CLI configuration.
     * @return  CLI configuration
     */
    CliConfig getConfig();


        /**
     * Terminates the command line session.
     * Also closes the connection to the controller if it's still open.
     */
    void terminateSession();

    /**
     * Checks whether the session has been terminated.
     * @return
     */
    boolean isTerminated();

    /**
     * Associates an object with key. The mapping is valid until this method is called with the same key value
     * and null as the new value for this key.
     * @param key the key
     * @param value the value to be associated with the key
     */
    void set(String key, Object value);

    /**
     * Returns the value the key was associated with using the set(key, value) method above.
     * @param key the key to fetch the value for
     * @return the value associated with the key or null, if the key wasn't associated with any non-null value.
     */
    Object get(String key);

    /**
     * Removes the value the key was associated with using the set(key, value) method above.
     * If the key isn't associated with any value, the method will return null.
     * @param key the key to be removed
     * @return the value associated with the key or null, if the key wasn't associated with any non-null value.
     */
    Object remove(String key);

    /**
     * Returns the model controller client or null if it hasn't been initialized.
     * @return the model controller client or null if it hasn't been initialized.
     */
    ModelControllerClient getModelControllerClient();

    /**
     * Connects the controller client using the default controller definition.
     *
     * The default controller will be identified as the default specified on starting the CLI will be used, if no controller was
     * specified on start up then the default defined in the CLI configuration will be used, if no default is defined then a
     * connection to http-remoting://localhost:9990 will be used instead.
     *
     * @throws CommandLineException in case the attempt to connect failed
     */
    void connectController() throws CommandLineException;

    /**
     * Connects to the controller specified.
     *
     * If the controller is null then the default specified on starting the CLI will be used, if no controller was specified on
     * start up then the default defined in the CLI configuration will be used, if no default is defined then a connection to
     * http-remoting://localhost:9990 will be used instead.
     *
     * @param controller the controller to connect to
     * @throws CommandLineException in case the attempt to connect failed
     */
    void connectController(String controller) throws CommandLineException;

    /**
     * Connects the controller client using the host and the port.
     * If the host is null, the default controller host will be used,
     * which is localhost.
     * If the port is less than zero, the default controller port will be used,
     * which is 9999.
     *
     * @deprecated Use {@link #connectController(String)} instead.
     *
     * @param host the host to connect with
     * @param port the port to connect on
     * @throws CommandLineException  in case the attempt to connect failed
     */
    @Deprecated
    void connectController(String host, int port) throws CommandLineException;

    /**
     * Bind the controller to an existing, connected client.
     */
    void bindClient(ModelControllerClient newClient, ControllerAddress address);

    /**
     * Closes the previously established connection with the controller client.
     * If the connection hasn't been established, the method silently returns.
     */
    void disconnectController();


     /**
     * The default address of the default controller to connect to.
     *
     * @return The default address.
     */
    ControllerAddress getDefaultControllerAddress();

    /**
     * Returns the host the controller client is connected to or
     * null if the connection hasn't been established yet.
     *
     * @return  the host the controller client is connected to or
     * null if the connection hasn't been established yet.
     */
    String getControllerHost();

    /**
     * Returns the port the controller client is connected to.
     *
     * @return  the port the controller client is connected to.
     */
    int getControllerPort();

    /**
     * Returns the current prefix.
     * @return current prefix
     */
    OperationRequestAddress getCurrentNodePath();

    /**
     * Returns the prefix formatter.
     * @return the prefix formatter.
     */
    NodePathFormatter getNodePathFormatter();

    /**
     * Checks whether the CLI is in the batch mode.
     * @return true if the CLI is in the batch mode, false - otherwise.
     */
    boolean isBatchMode();

    /**
     * Returns batch manager.
     * @return batch manager
     */
    BatchManager getBatchManager();

    /**
     * Builds an operation request from the passed in command line.
     * If the line contains a command, the command must supported the batch mode,
     * otherwise an exception will thrown.
     *
     * @param line the command line which can be an operation request or a command that can be translated into an operation request.
     * @return  the operation request
     * @throws CommandFormatException  if the operation request couldn't be built.
     */
    BatchedCommand toBatchedCommand(String line) throws CommandFormatException;

    /**
     * Builds a DMR request corresponding to the command or the operation.
     * If the line contains a command, the corresponding command handler
     * must implement org.jboss.cli.OperationCommand interface,
     * in other words the command must translate into an operation request,
     * otherwise an exception will be thrown.
     *
     * @param line  command or an operation to build a DMR request for
     * @return  DMR request corresponding to the line
     * @throws CommandFormatException  thrown in case the line couldn't be
     * translated into a DMR request
     */
    ModelNode buildRequest(String line) throws CommandFormatException;

    /**
     * Indicates whether the CLI is in the domain mode or standalone one (assuming established
     * connection to the controller).
     * @return  true if the CLI is connected to the domain controller, otherwise false.
     */
    boolean isDomainMode();

    /**
     * Returns value that should be used as the exit code of the JVM process.
     * @return  JVM exit code
     */
    int getExitCode();

    /**
     * Executes a command or an operation. Or, if the context is in the batch mode
     * and the command is allowed in the batch, adds the command (or the operation)
     * to the currently active batch.
     * NOTE: errors are not handled by this method, they won't affect the exit code or
     * even be logged. Error handling is the responsibility of the caller.
     *
     * @param line  command or operation to handle
     * @throws CommandFormatException  in case there was an error handling the command or operation
     */
    void handle(String line) throws CommandLineException;

    /**
     * Executes a command or an operation. Or, if the context is in the batch mode
     * and the command is allowed in the batch, adds the command (or the operation)
     * to the currently active batch.
     * NOTE: unlike handle(String line), this method catches CommandLineException
     * exceptions thrown by command handlers, logs them and sets the exit code
     * status to indicate that the command or the operation has failed.
     * It's up to the caller to check the exit code with getExitCode()
     * to find out whether the command or the operation succeeded or failed.
     *
     * @param line  command or operation to handle
     * @throws CommandFormatException  in case there was an error handling the command or operation
     */
    void handleSafe(String line);

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
     * Command argument or operation parameter values may contain system properties.
     * If this method returns true then the CLI will try to resolve
     * the system properties before sending the operation request to the controller.
     * Otherwise, the resolution will happen on the server side.
     *
     * @return true if system properties in the operation parameter values
     * should be resolved by the CLI before the request is sent to the controller,
     * false if system properties should be resolved on the server side.
     */
    boolean isResolveParameterValues();

    /**
     * Command argument or operation parameter values may contain system properties.
     * If this property is set to true then the CLI will try to resolve
     * the system properties before sending the operation request to the controller.
     * Otherwise, the resolution will happen on the server side.
     *
     * @param resolve  true if system properties in the operation parameter values
     * should be resolved by the CLI before the request is sent to the controller,
     * false if system properties should be resolved on the server side.
     */
    void setResolveParameterValues(boolean resolve);

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

    ControllerAddressResolver getAddressResolver();


    CliSSLContext getSSLContext();

    boolean doDisableLocalAuth();

    int getConnectionTimeout();

    void handleClose();
}
