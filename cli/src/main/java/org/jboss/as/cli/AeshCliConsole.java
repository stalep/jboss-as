/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli;

import org.jboss.aesh.console.AeshConsole;
import org.jboss.aesh.console.AeshConsoleBuilder;
import org.jboss.aesh.console.Prompt;
import org.jboss.aesh.console.command.invocation.CommandInvocationServices;
import org.jboss.aesh.console.command.registry.AeshCommandRegistryBuilder;
import org.jboss.aesh.console.command.registry.CommandRegistry;
import org.jboss.aesh.console.settings.SettingsBuilder;
import org.jboss.as.cli.command.Cd;
import org.jboss.as.cli.command.Clear;
import org.jboss.as.cli.command.Connect;
import org.jboss.as.cli.command.Exit;
import org.jboss.as.cli.command.Logout;
import org.jboss.as.cli.command.Ls;
import org.jboss.as.cli.command.Quit;
import org.jboss.as.cli.provider.CliCommandInvocationProvider;
import org.jboss.as.cli.provider.CliCompleterInvocationProvider;
import org.jboss.as.cli.provider.CliConverterInvocationProvider;
import org.jboss.as.cli.provider.CliManProvider;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AeshCliConsole {

    private AeshConsole console;
    private CommandContext commandContext;
    private CommandRegistry commandRegistry;
    private static final String PROVIDER = "JBOSS_CLI";

    public AeshCliConsole(CommandContext commandContext) {
        this.commandContext = commandContext;

        setupConsole(null, null);
    }

    public void startConsole() {
        if(console != null)
            console.start();
    }

    public AeshCliConsole(CommandContext commandContext,
                          InputStream consoleInput, OutputStream consoleOutput) {
        this.commandContext = commandContext;

        setupConsole(consoleInput, consoleOutput);
    }

    private void setupConsole(InputStream consoleInput, OutputStream consoleOutput) {

        SettingsBuilder settingsBuilder = new SettingsBuilder();
        if(consoleInput != null)
            settingsBuilder.inputStream(consoleInput);
        if(consoleOutput != null)
            settingsBuilder.outputStream(new PrintStream(consoleOutput));

        settingsBuilder
                .logging(true)
                .readInputrc(false)
                .enableMan(true);

        CommandInvocationServices services = new CommandInvocationServices();
        services.registerProvider(PROVIDER, new CliCommandInvocationProvider(commandContext));

        commandRegistry = createCommandRegistry();

        console = new AeshConsoleBuilder()
                .commandRegistry(commandRegistry)
                .settings(settingsBuilder.create())
                .commandInvocationProvider(services)
                .completerInvocationProvider(new CliCompleterInvocationProvider(commandContext))
                .commandNotFoundHandler(new CliCommandNotFound())
                .converterInvocationProvider(new CliConverterInvocationProvider(commandContext))
                //.validatorInvocationProvider(new CliValidatorInvocationProvider(commandContext))
                .manProvider(new CliManProvider())
                .prompt(new Prompt("[disconnected /] "))
                .create();

        console.setCurrentCommandInvocationProvider(PROVIDER);

    }

    private CommandRegistry createCommandRegistry() {
        return new AeshCommandRegistryBuilder()
                .command(Quit.class)
                .command(Exit.class)
                .command(Ls.class)
                .command(Connect.class)
                .command(Cd.class)
                .command(Clear.class)
                .command(Logout.class)
                .create();
    }
}
