/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.validator;

import org.jboss.aesh.cl.validator.OptionValidator;
import org.jboss.aesh.cl.validator.OptionValidatorException;
import org.jboss.as.cli.provider.CliValidatorInvocationImpl;
import org.jboss.as.cli.operation.OperationRequestAddress;

/**
 * Does not do anything atm
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class OperationRequestAddressValidator implements OptionValidator<CliValidatorInvocationImpl> {

    @Override
    public void validate(CliValidatorInvocationImpl validatorInvocation) throws OptionValidatorException {
        OperationRequestAddress address = (OperationRequestAddress) validatorInvocation.getValue();

        if(validatorInvocation.getCommandContext() == null)
            throw new OptionValidatorException("CONTEXT IS NULL");
    }
}
