package org.jboss.as.cli.command;

import org.jboss.aesh.cl.Arguments;
import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.cl.Option;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;
import org.jboss.as.cli.completer.PathOptionCompleter;
import org.jboss.as.cli.converter.OperationRequestAddressConverter;
import org.jboss.as.cli.operation.OperationRequestAddress;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "cd", description = " [node_path]%n Changes the current node path to the argument.")
public class Cd implements Command<CliCommandInvocation> {

    @Arguments(completer = PathOptionCompleter.class,
            converter = OperationRequestAddressConverter.class)
            //validator = ChangeNodeValidator.class)
    private List<OperationRequestAddress> arguments;

    @Option(hasValue = false)
    private boolean help;

    @Override
    public CommandResult execute(CliCommandInvocation cliCommandInvocation) throws IOException {
        if(help) {
            cliCommandInvocation.getShell().out().print(cliCommandInvocation.getHelpInfo("cd"));
        }
        else {
            if(arguments != null &&arguments.size() > 0) {
                cliCommandInvocation.getCommandContext().setCurrentNodePath(arguments.get(0));

                cliCommandInvocation.updatePrompt();
            }
        }
        return CommandResult.SUCCESS;
    }

}
