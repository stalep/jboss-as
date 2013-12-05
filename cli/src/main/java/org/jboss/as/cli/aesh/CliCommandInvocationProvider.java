/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh;

import org.jboss.aesh.console.command.invocation.CommandInvocation;
import org.jboss.aesh.console.command.invocation.CommandInvocationProvider;
import org.jboss.as.cli.aesh.handlers.CliCommandInvocation;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliCommandInvocationProvider implements CommandInvocationProvider<CliCommandInvocation> {

    private final CliConnectionContext connectionContext;

    public CliCommandInvocationProvider(final CliConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
    }
    @Override
    public CliCommandInvocation enhanceCommandInvocation(CommandInvocation commandInvocation) {
        return new CliCommandInvocation(connectionContext, commandInvocation);
    }
}
