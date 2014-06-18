/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.provider;

import org.jboss.aesh.console.AeshContext;
import org.jboss.as.cli.CommandContext;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliValidatorInvocationImpl implements CliValidatorInvocation {

    private CommandContext commandContext;
    private Object value;

    public CliValidatorInvocationImpl(CommandContext commandContext, Object value) {
        this.commandContext = commandContext;
        this.value = value;
    }

    @Override
    public CommandContext getCommandContext() {
        return commandContext;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Object getCommand() {
        return null;
    }

    @Override
    public AeshContext getAeshContext() {
        return null;
    }
}
