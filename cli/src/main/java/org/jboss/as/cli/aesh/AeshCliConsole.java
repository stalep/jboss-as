/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh;

import org.jboss.aesh.console.AeshConsole;
import org.jboss.aesh.console.AeshConsoleBuilder;
import org.jboss.aesh.console.Prompt;
import org.jboss.aesh.console.command.invocation.CommandInvocationServices;
import org.jboss.aesh.console.command.registry.AeshCommandRegistryBuilder;
import org.jboss.aesh.console.command.registry.CommandRegistry;
import org.jboss.aesh.console.settings.SettingsBuilder;
import org.jboss.as.cli.aesh.commands.LsCommand;
import org.jboss.as.cli.aesh.commands.QuitCommand;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AeshCliConsole {

    private AeshConsole console;
    private ConnectionContext connectionContext;
    private CommandRegistry commandRegistry;
    private static final String PROVIDER = "JBOSS_CLI";

    public AeshCliConsole(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;

        setupConsole(null, null);
    }

    public void startConsole() {
        if(console != null)
            console.start();
    }

    public AeshCliConsole(ConnectionContext connectionContext,
                          InputStream consoleInput, OutputStream consoleOutput) {
        this.connectionContext = connectionContext;

        setupConsole(consoleInput, consoleOutput);
    }

    private void setupConsole(InputStream consoleInput, OutputStream consoleOutput) {

        SettingsBuilder settingsBuilder = new SettingsBuilder();
        if(consoleInput != null)
            settingsBuilder.inputStream(consoleInput);
        if(consoleOutput != null)
            settingsBuilder.outputStream(new PrintStream(consoleOutput));

        CommandInvocationServices services = new CommandInvocationServices();
        services.registerProvider(PROVIDER, new CliCommandInvocationProvider(connectionContext));


        commandRegistry = createCommandRegistry();

        console = new AeshConsoleBuilder()
                .commandRegistry(commandRegistry)
                .settings(settingsBuilder.create())
                .commandInvocationProvider(services)
                .completerInvocationProvider(new CliCompleterInvocationProvider(connectionContext))
                .prompt(new Prompt("[aesh@test]$ "))
                .create();

        console.setCurrentCommandInvocationProvider(PROVIDER);

    }

    private CommandRegistry createCommandRegistry() {
        return new AeshCommandRegistryBuilder()
                .command(QuitCommand.class)
                .command(LsCommand.class)
                .create();
    }
}
