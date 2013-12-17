/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh;

import org.jboss.aesh.console.command.completer.CompleterInvocation;
import org.jboss.aesh.console.command.completer.CompleterInvocationProvider;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliCompleterInvocationProvider implements CompleterInvocationProvider<CliCompleterInvocation> {

    private CliConnectionContext ctx;

    public CliCompleterInvocationProvider(CliConnectionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public CliCompleterInvocation enhanceCompleterInvocation(CompleterInvocation completerInvocation) {
        return new CliCompleterInvocation(completerInvocation, ctx);
    }
}
