/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh;

import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.completer.CompleterInvocation;
import org.jboss.aesh.terminal.TerminalString;

import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliCompleterInvocation implements CompleterInvocation {

    private CompleterInvocation delegate;

    private CliConnectionContext connectionContext;

    public CliCompleterInvocation(CompleterInvocation delegate, CliConnectionContext ctx) {
        this.delegate = delegate;
        this.connectionContext = ctx;
    }

    @Override
    public String getGivenCompleteValue() {
        return delegate.getGivenCompleteValue();
    }

    @Override
    public Command getCommand() {
        return delegate.getCommand();
    }

    @Override
    public List<TerminalString> getCompleterValues() {
        return delegate.getCompleterValues();
    }

    @Override
    public void setCompleterValues(List<String> strings) {
        delegate.setCompleterValues(strings);
    }

    @Override
    public void setCompleterValuesTerminalString(List<TerminalString> terminalStrings) {
        delegate.setCompleterValuesTerminalString(terminalStrings);
    }

    @Override
    public void clearCompleterValues() {
        delegate.clearCompleterValues();
    }

    @Override
    public void addAllCompleterValues(List<String> strings) {
        delegate.addAllCompleterValues(strings);
    }

    @Override
    public void addCompleterValue(String s) {
        delegate.addCompleterValue(s);
    }

    @Override
    public void addCompleterValueTerminalString(TerminalString terminalString) {
        delegate.addCompleterValueTerminalString(terminalString);
    }

    @Override
    public boolean isAppendSpace() {
        return delegate.isAppendSpace();
    }

    @Override
    public void setAppendSpace(boolean b) {
        delegate.setAppendSpace(b);
    }

    public CliConnectionContext getConnectionContext() {
        return connectionContext;
    }
}
