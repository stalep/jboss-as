/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.handlers;

import org.jboss.aesh.console.Prompt;
import org.jboss.aesh.console.command.ConsoleCommand;
import org.jboss.aesh.console.command.invocation.CommandInvocation;
import org.jboss.aesh.console.command.registry.CommandRegistry;
import org.jboss.aesh.console.operator.ControlOperator;
import org.jboss.aesh.terminal.Shell;
import org.jboss.as.cli.aesh.CliConnectionContext;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliCommandInvocation implements CommandInvocation {

    private final CommandInvocation commandInvocation;
    private final CliConnectionContext ctx;

    public CliCommandInvocation(final CliConnectionContext ctx, CommandInvocation commandInvocation) {
        this.ctx = ctx;
        this.commandInvocation = commandInvocation;
    }

    public final CliConnectionContext getConnectionContext() {
        return ctx;
    }

    @Override
    public ControlOperator getControlOperator() {
        return commandInvocation.getControlOperator();
    }

    @Override
    public CommandRegistry getCommandRegistry() {
        return commandInvocation.getCommandRegistry();
    }

    @Override
    public void attachConsoleCommand(ConsoleCommand consoleCommand) {
        commandInvocation.attachConsoleCommand(consoleCommand);
    }

    @Override
    public Shell getShell() {
        return commandInvocation.getShell();
    }

    @Override
    public void setPrompt(Prompt prompt) {
        commandInvocation.setPrompt(prompt);
    }

    @Override
    public Prompt getPrompt() {
        return commandInvocation.getPrompt();
    }

    @Override
    public String getHelpInfo(String s) {
        return commandInvocation.getHelpInfo(s);
    }

    @Override
    public void stop() {
        commandInvocation.stop();
    }
}
