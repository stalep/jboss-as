/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.command;

import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;

import java.io.IOException;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "clear", description = "clear the terminal")
public class Clear implements Command<CliCommandInvocation> {
    @Override
    public CommandResult execute(CliCommandInvocation commandInvocation) throws IOException, InterruptedException {
        commandInvocation.getShell().clear();
        return CommandResult.SUCCESS;
    }
}
