/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.providers;

import org.jboss.aesh.console.command.validator.ValidatorInvocation;
import org.jboss.aesh.console.command.validator.ValidatorInvocationProvider;
import org.jboss.as.cli.CommandContext;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliValidatorInvocationProvider implements ValidatorInvocationProvider<CliValidatorInvocationImpl> {

    private final CommandContext commandContext;

    public CliValidatorInvocationProvider(CommandContext commandContext) {
        this.commandContext = commandContext;
    }

    @Override
    public CliValidatorInvocationImpl enhanceValidatorInvocation(ValidatorInvocation validatorInvocation) {
        return new CliValidatorInvocationImpl(commandContext, validatorInvocation.getValue());
    }
}
