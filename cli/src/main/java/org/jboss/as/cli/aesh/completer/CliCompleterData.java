/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.completer;

import org.jboss.aesh.cl.completer.CompleterData;
import org.jboss.aesh.console.command.Command;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliCompleterData extends CompleterData {

    public CliCompleterData(String completeValue, Command command) {
        super(completeValue, command);
    }


}
