/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.validator;

import org.jboss.aesh.cl.validator.OptionValidator;
import org.jboss.aesh.cl.validator.OptionValidatorException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.provider.CliValidatorInvocationImpl;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.dmr.ModelNode;

import java.io.IOException;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ChangeNodeValidator implements OptionValidator<CliValidatorInvocationImpl> {

    @Override
    public void validate(CliValidatorInvocationImpl validatorInvocation) throws OptionValidatorException {
        assertValid(validatorInvocation.getCommandContext(),
                (OperationRequestAddress) validatorInvocation.getValue());
    }

    private void assertValid(CommandContext ctx, OperationRequestAddress addr) throws OptionValidatorException {
        ModelNode req = new ModelNode();
        req.get(Util.ADDRESS).setEmptyList();
        req.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
        final ModelNode addressValue = req.get(Util.VALUE);
        String lastType = null;
        if(addr.isEmpty()) {
            addressValue.setEmptyList();
        } else {
            for(OperationRequestAddress.Node node : addr) {
                if(node.getName() != null) {
                    addressValue.add(node.getType(), node.getName());
                } else {
                    lastType = node.getType();
                }
            }
        }
        ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(req);
        } catch (IOException e) {
            throw new OptionValidatorException("Failed to validate address."+ e);
        }
        ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            throw new OptionValidatorException("Failed to validate address: the response from the controller doesn't contain result.");
        }
        final ModelNode valid = result.get(Util.VALID);
        if(!valid.isDefined()) {
            throw new OptionValidatorException("Failed to validate address: the result doesn't contain 'valid' property.");
        }
        if(!valid.asBoolean()) {
            final String msg;
            if(result.hasDefined(Util.PROBLEM)) {
                msg = result.get(Util.PROBLEM).asString();
            } else {
                msg = "Invalid target address.";
            }
            throw new OptionValidatorException(msg);
        }

        if(lastType != null) {
            req = new ModelNode();
            req.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);
            final ModelNode addrNode = req.get(Util.ADDRESS);
            if(addr.isEmpty()) {
                addrNode.setEmptyList();
            } else {
                for(OperationRequestAddress.Node node : addr) {
                    if(node.getName() != null) {
                        addrNode.add(node.getType(), node.getName());
                    }
                }
            }
            try {
                response = ctx.getModelControllerClient().execute(req);
            } catch (IOException e) {
                throw new OptionValidatorException("Failed to validate address."+e);
            }
            result = response.get(Util.RESULT);
            if(!result.isDefined()) {
                throw new OptionValidatorException("Failed to validate address: the response from the controller doesn't contain result.");
            }
            for(ModelNode type : result.asList()) {
                if(lastType.equals(type.asString())) {
                    return;
                }
            }
            throw new OptionValidatorException("Invalid target address: " + lastType + " doesn't exist.");
        }
    }
}
