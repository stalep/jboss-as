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
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.aesh.completer.PathOptionCompleter;
import org.jboss.as.cli.aesh.converter.OperationRequestAddressConverter;
import org.jboss.as.cli.aesh.validator.ChangeNodeValidator;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "cd", description = " [node_path]%n Changes the current node path to the argument.")
public class CdCommand implements Command<CliCommandInvocation> {

    @Arguments(completer = PathOptionCompleter.class, converter = OperationRequestAddressConverter.class,
            validator = ChangeNodeValidator.class)
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

                final OperationRequestAddress tmp = new DefaultOperationRequestAddress(cliCommandInvocation.getCommandContext().getCurrentNodePath());


                //ParserUtil.parseOperationRequest(tmp, new DefaultCallbackHandler(tmp));

            }
        }
        return CommandResult.SUCCESS;
    }

}
