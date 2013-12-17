/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.providers;

import org.jboss.aesh.console.command.converter.ConverterInvocation;
import org.jboss.as.cli.CommandContext;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliConverterInvocation implements ConverterInvocation {

    private final CommandContext commandContext;
    private final String input;

    public CliConverterInvocation(CommandContext commandContext, String input) {
        this.input = input;
        this.commandContext = commandContext;
    }

    @Override
    public String getInput() {
        return input;
    }

    public CommandContext getCommandContext() {
        return commandContext;
    }
}
