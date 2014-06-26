/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.clustering.infinispan.subsystem;

import org.infinispan.transaction.LockingMode;
import org.jboss.as.clustering.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Resource description for the addressable resource /subsystem=infinispan/cache-container=X/cache=Y/transaction=TRANSACTION
 *
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 */
public class TransactionResourceDefinition extends SimpleResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(ModelKeys.TRANSACTION, ModelKeys.TRANSACTION_NAME);

    // attributes
    // cache mode required, txn mode not
    static final SimpleAttributeDefinition LOCKING = new SimpleAttributeDefinitionBuilder(ModelKeys.LOCKING, ModelType.STRING, true)
            .setXmlName(Attribute.LOCKING.getLocalName())
            .setAllowExpression(true)
            .setValidator(new EnumValidator<>(LockingMode.class, true, false))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setDefaultValue(new ModelNode().set(LockingMode.PESSIMISTIC.name()))
            .build();

    static final SimpleAttributeDefinition MODE = new SimpleAttributeDefinitionBuilder(ModelKeys.MODE, ModelType.STRING, true)
            .setXmlName(Attribute.MODE.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setValidator(new EnumValidator<>(TransactionMode.class, true, true))
            .setDefaultValue(new ModelNode().set(TransactionMode.NONE.name()))
            .build();

    static final SimpleAttributeDefinition STOP_TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelKeys.STOP_TIMEOUT, ModelType.LONG, true)
            .setXmlName(Attribute.STOP_TIMEOUT.getLocalName())
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setDefaultValue(new ModelNode().set(30000))
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { MODE, STOP_TIMEOUT, LOCKING };

    private final boolean allowRuntimeOnlyRegistration;

    TransactionResourceDefinition(boolean allowRuntimeOnlyRegistration) {
        super(PATH, InfinispanExtension.getResourceDescriptionResolver(ModelKeys.TRANSACTION), new ReloadRequiredAddStepHandler(ATTRIBUTES), ReloadRequiredRemoveStepHandler.INSTANCE);
        this.allowRuntimeOnlyRegistration = allowRuntimeOnlyRegistration;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        // check that we don't need a special handler here?
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            registration.registerReadWriteAttribute(attr, null, writeHandler);
        }

        if (this.allowRuntimeOnlyRegistration) {
            // register any metrics
            OperationStepHandler handler = new TransactionMetricsHandler();
            for (TransactionMetric metric: TransactionMetric.values()) {
                registration.registerMetric(metric.getDefinition(), handler);
            }
        }
    }
}
