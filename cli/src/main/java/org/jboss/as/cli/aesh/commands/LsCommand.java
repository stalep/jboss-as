/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.commands;

import org.jboss.aesh.cl.Arguments;
import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.cl.Option;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "ls", description = "list resources")
public class LsCommand implements Command<CliCommandInvocation> {

    @Arguments
    private List<String> args;

    @Option(name = "list", shortName = 'l', required = false)
    private String list;

    @Override
    public CommandResult execute(CliCommandInvocation cliCommandInvocation) throws IOException {
        cliCommandInvocation.getShell().out().println("here we'll display some ls info");
        return CommandResult.SUCCESS;
    }
}
