/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.commands;

import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;

import java.io.IOException;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "exit", description = "exits the session")
public class ExitCommand implements Command<CliCommandInvocation> {

    @Override
    public CommandResult execute(CliCommandInvocation commandInvocation) throws IOException {
        commandInvocation.getCommandContext().terminateSession();
        commandInvocation.stop();
        return CommandResult.SUCCESS;
    }
}
