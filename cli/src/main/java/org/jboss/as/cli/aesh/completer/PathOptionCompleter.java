/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.completer;

import org.jboss.aesh.cl.completer.OptionCompleter;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.aesh.providers.CliCompleterInvocation;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class PathOptionCompleter implements OptionCompleter<CliCompleterInvocation> {

    @Override
    public void complete(CliCompleterInvocation cliCompleterInvocation) {
        final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler();
        try {
            parsedOp.parseOperation(cliCompleterInvocation.getCommandContext().getCurrentNodePath(),
                    cliCompleterInvocation.getGivenCompleteValue());

            List<String> candidates = new ArrayList<String>();
            int cursor = OperationRequestCompleter.INSTANCE.complete(cliCompleterInvocation.getCommandContext(), parsedOp,
                    cliCompleterInvocation.getGivenCompleteValue(),
                    cliCompleterInvocation.getGivenCompleteValue().length(), candidates);
            cliCompleterInvocation.addAllCompleterValues(candidates);
        }
        catch (CommandFormatException e) {
            e.printStackTrace();
        }
    }
}
