package org.jboss.as.cli.command;

import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;

import java.io.IOException;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "logout", description = "logout from the current session")
public class Logout implements Command<CliCommandInvocation> {
    @Override
    public CommandResult execute(CliCommandInvocation commandInvocation) throws IOException, InterruptedException {
        if(commandInvocation.getCommandContext().getModelControllerClient() != null) {
            commandInvocation.getCommandContext().terminateSession();
            commandInvocation.updatePrompt();
            return CommandResult.SUCCESS;
        }

        return CommandResult.FAILURE;
    }
}
