package org.jboss.as.cli.command;

import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;

import java.io.IOException;

@CommandDefinition(name = "exit", description = "exit the cli")
public class Exit implements Command<CliCommandInvocation> {

    @Override
    public CommandResult execute(CliCommandInvocation commandInvocation) throws IOException, InterruptedException {
        commandInvocation.getCommandContext().terminateSession();
        commandInvocation.stop();
        return CommandResult.SUCCESS;
    }
}
