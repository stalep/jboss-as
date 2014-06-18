/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.provider;

import org.jboss.aesh.console.AeshContext;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.completer.CompleterInvocation;
import org.jboss.aesh.terminal.TerminalString;
import org.jboss.as.cli.CommandContext;

import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliCompleterInvocation implements CompleterInvocation {

    private CompleterInvocation delegate;

    private CommandContext commandContext;

    public CliCompleterInvocation(CompleterInvocation delegate, CommandContext ctx) {
        this.delegate = delegate;
        this.commandContext = ctx;
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

    @Override
    public void setIgnoreOffset(boolean ignoreOffset) {
        delegate.setIgnoreOffset(ignoreOffset);
    }

    @Override
    public boolean doIgnoreOffset() {
        return delegate.doIgnoreOffset();
    }

    @Override
    public void setOffset(int offset) {
        delegate.setOffset(offset);
    }

    @Override
    public int getOffset() {
        return delegate.getOffset();
    }

    @Override
    public void setIgnoreStartsWith(boolean ignoreStartsWith) {
        delegate.setIgnoreStartsWith(ignoreStartsWith);
    }

    @Override
    public boolean isIgnoreStartsWith() {
        return delegate.isIgnoreStartsWith();
    }

    @Override
    public AeshContext getAeshContext() {
        return delegate.getAeshContext();
    }

    public CommandContext getCommandContext() {
        return commandContext;
    }
}
