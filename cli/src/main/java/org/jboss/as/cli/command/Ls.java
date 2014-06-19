package org.jboss.as.cli.command;

import org.jboss.aesh.cl.Arguments;
import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.cl.Option;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.completer.PathOptionCompleter;
import org.jboss.as.cli.converter.OperationRequestAddressConverter;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@CommandDefinition(name = "ls", description = "list resource node")
public class Ls implements Command<CliCommandInvocation> {

    @Option(name = "list", shortName = 'l', required = false, hasValue = false)
    private boolean list;

    @Arguments(completer = PathOptionCompleter.class,
            converter = OperationRequestAddressConverter.class, defaultValue = ".")
            //validator = OperationRequestAddressValidator.class)
    private List<OperationRequestAddress> args;

    @Override
    public CommandResult execute(CliCommandInvocation commandInvocation) throws IOException, InterruptedException {
        if(args != null && args.size() > 0)
            try {
                parse(commandInvocation);
            }
            catch (CommandFormatException e) {
                e.printStackTrace();
            }

        return CommandResult.SUCCESS;
    }


    private void parse(CliCommandInvocation cliCommandInvocation) throws CommandFormatException {

        CommandContext ctx = cliCommandInvocation.getCommandContext();

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        String nodePath = null; //this.nodePath.getValue(parsedCmd);

        final OperationRequestAddress address = args.get(0);
        /*
        if (nodePath != null) {
            address = new DefaultOperationRequestAddress(ctx.getCurrentNodePath());
            CommandLineParser.CallbackHandler handler = new DefaultCallbackHandler(address);

            // this is for correct parsing of escaped characters
            nodePath = ctx.getArgumentsString();


            if(list) {
                nodePath = nodePath.trim();
                if(nodePath.startsWith("-l ")) {
                    nodePath = nodePath.substring(3);
                } else {
                    nodePath = nodePath.substring(0, nodePath.length() - 3);
                }
            }
            ctx.getCommandLineParser().parse(nodePath, handler);
        } else {
            address = new DefaultOperationRequestAddress(ctx.getCurrentNodePath());
        }
        */

        List<String> names = null;
        if(address.endsOnType()) {
            final String type = address.getNodeType();
            address.toParentNode();
            names = Util.getNodeNames(ctx.getModelControllerClient(), address, type);

            for(String item : names) {
                cliCommandInvocation.getShell().out().println(item);
            }
            cliCommandInvocation.getShell().out().flush();
            //printList(ctx, names, l.isPresent(parsedCmd));

            return;

        }

        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);

        {
            final ModelNode typesRequest = new ModelNode();
            typesRequest.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);
            final ModelNode addressNode = typesRequest.get(Util.ADDRESS);
            if (address.isEmpty()) {
                addressNode.setEmptyList();
            } else {
                Iterator<OperationRequestAddress.Node> iterator = address.iterator();
                while (iterator.hasNext()) {
                    OperationRequestAddress.Node node = iterator.next();
                    if (node.getName() != null) {
                        addressNode.add(node.getType(), node.getName());
                    } else if (iterator.hasNext()) {
                        throw new OperationFormatException(
                                "Expected a node name for type '"
                                        + node.getType()
                                        + "' in path '"
                                        + ctx.getNodePathFormatter().format(
                                        address) + "'");
                    }
                }
            }
            steps.add(typesRequest);
        }

        {
            final ModelNode resourceRequest = new ModelNode();
            resourceRequest.get(Util.OPERATION).set(Util.READ_RESOURCE);
            final ModelNode addressNode = resourceRequest.get(Util.ADDRESS);
            if (address.isEmpty()) {
                addressNode.setEmptyList();
            } else {
                Iterator<OperationRequestAddress.Node> iterator = address.iterator();
                while (iterator.hasNext()) {
                    OperationRequestAddress.Node node = iterator.next();
                    if (node.getName() != null) {
                        addressNode.add(node.getType(), node.getName());
                    } else if (iterator.hasNext()) {
                        throw new OperationFormatException(
                                "Expected a node name for type '"
                                        + node.getType()
                                        + "' in path '"
                                        + ctx.getNodePathFormatter().format(
                                        address) + "'");
                    }
                }
            }
            resourceRequest.get(Util.INCLUDE_RUNTIME).set(Util.TRUE);
            steps.add(resourceRequest);
        }

        final String[] additionalProps = null;
        if (list) {
            steps.add(Util.buildRequest(ctx, address, Util.READ_RESOURCE_DESCRIPTION));
            /*
            final Set<String> argNames = parsedCmd.getPropertyNames();
            if (argNames.size() > 1) {
                additionalProps = new String[argNames.size() - 1];
                int i = 0;
                for (String arg : argNames) {
                    if (arg.equals("list")) {
                        continue;
                    }
                    final String prop;
                    if (arg.length() > 1 && arg.charAt(0) == '-') {
                        if (arg.charAt(1) == '-') {
                            prop = arg.substring(2);
                        } else {
                            prop = arg.substring(1);
                        }
                    } else {
                        prop = arg;
                    }
                    additionalProps[i++] = prop;
                }
            } else {
                additionalProps = null;
            }
            */
        } else {
            //additionalProps = null;
        }

        ModelNode outcome;
        try {
            outcome = ctx.getModelControllerClient().execute(composite);
        } catch (IOException e) {
            throw new CommandFormatException("Failed to read resource: "
                    + e.getLocalizedMessage(), e);
        }

        if (Util.isSuccess(outcome)) {
            if (outcome.hasDefined(Util.RESULT)) {
                ModelNode resultNode = outcome.get(Util.RESULT);

                ModelNode attrDescriptions = null;
                ModelNode childDescriptions = null;
                if (resultNode.hasDefined(Util.STEP_3)) {
                    final ModelNode stepOutcome = resultNode.get(Util.STEP_3);
                    if (Util.isSuccess(stepOutcome)) {
                        if (stepOutcome.hasDefined(Util.RESULT)) {
                            final ModelNode descrResult = stepOutcome.get(Util.RESULT);
                            if (descrResult.hasDefined(Util.ATTRIBUTES)) {
                                attrDescriptions = descrResult.get(Util.ATTRIBUTES);
                            }
                            if (descrResult.hasDefined(Util.CHILDREN)) {
                                childDescriptions = descrResult.get(Util.CHILDREN);
                            }
                        } else {
                            throw new CommandFormatException("Result is not available for read-resource-description request: " + outcome);
                        }
                    } else {
                        throw new CommandFormatException("Failed to get resource description: " + outcome);
                    }
                }

                List<String> typeNames = null;
                if (resultNode.hasDefined(Util.STEP_1)) {
                    ModelNode typesOutcome = resultNode.get(Util.STEP_1);
                    if (Util.isSuccess(typesOutcome)) {
                        if (typesOutcome.hasDefined(Util.RESULT)) {
                            final ModelNode resourceResult = typesOutcome.get(Util.RESULT);
                            final List<ModelNode> types = resourceResult.asList();
                            if (!types.isEmpty()) {
                                typeNames = new ArrayList<>();
                                for (ModelNode type : types) {
                                    typeNames.add(type.asString());
                                }
                                if (childDescriptions == null && attrDescriptions == null) {
                                    names = typeNames;
                                }
                            }
                        } else {
                            throw new CommandFormatException("Result is not available for read-children-types request: " + outcome);
                        }
                    } else {
                        throw new CommandFormatException("Failed to fetch type names: " + outcome);
                    }
                } else {
                    throw new CommandFormatException("The result for children type names is not available: " + outcome);
                }

                if (resultNode.hasDefined(Util.STEP_2)) {
                    ModelNode resourceOutcome = resultNode.get(Util.STEP_2);
                    if (Util.isSuccess(resourceOutcome)) {
                        if (resourceOutcome.hasDefined(Util.RESULT)) {
                            final ModelNode resourceResult = resourceOutcome.get(Util.RESULT);
                            final List<Property> props = resourceResult.asPropertyList();
                            if (!props.isEmpty()) {
                                final SimpleTable attrTable;
                                if (attrDescriptions == null) {
                                    attrTable = null;
                                } else {
                                    if (additionalProps != null) {
                                        String[] headers = new String[3 + additionalProps.length];
                                        headers[0] = "ATTRIBUTE";
                                        headers[1] = "VALUE";
                                        headers[2] = "TYPE";
                                        int i = 3;
                                        for (String additional : additionalProps) {
                                            headers[i++] = additional.toUpperCase(Locale.ENGLISH);
                                        }
                                        attrTable = new SimpleTable(headers);
                                    } else {
                                        attrTable = new SimpleTable(new String[] { "ATTRIBUTE", "VALUE", "TYPE" });
                                    }
                                }
                                SimpleTable childrenTable = childDescriptions == null ? null : new SimpleTable(new String[] { "CHILD", "MIN-OCCURS", "MAX-OCCURS" });
                                if (typeNames == null && attrTable == null && childrenTable == null) {
                                    typeNames = new ArrayList<>();
                                    names = typeNames;
                                }

                                for (Property prop : props) {
                                    final StringBuilder buf = new StringBuilder();
                                    if (typeNames == null || !typeNames.contains(prop.getName())) {
                                        if (attrDescriptions == null) {
                                            buf.append(prop.getName());
                                            buf.append('=');
                                            buf.append(prop.getValue().asString());
                                            // TODO the value should be formatted nicer but the current
                                            // formatter uses new lines for complex value which doesn't work here
                                            // final ModelNode value = prop.getValue();
                                            // ModelNodeFormatter.Factory.forType(value.getType()).format(buf, 0, value);
                                            typeNames.add(buf.toString());
                                            buf.setLength(0);
                                        } else {
                                            final String[] line = new String[attrTable.columnsTotal()];
                                            line[0] = prop.getName();
                                            line[1] = prop.getValue().asString();
                                            if (attrDescriptions.hasDefined(prop.getName())) {
                                                final ModelNode attrDescr = attrDescriptions.get(prop.getName());
                                                line[2] = getAsString(attrDescr, Util.TYPE);
                                                if (additionalProps != null) {
                                                    int i = 3;
                                                    for (String additional : additionalProps) {
                                                        line[i++] = getAsString(attrDescr, additional);
                                                    }
                                                }
                                            } else {
                                                for (int i = 2; i < line.length; ++i) {
                                                    line[i] = "n/a";
                                                }
                                            }
                                            attrTable.addLine(line);
                                        }
                                    } else if (childDescriptions != null) {
                                        if (childDescriptions.hasDefined(prop.getName())) {
                                            final ModelNode childDescr = childDescriptions.get(prop.getName());
                                            final Integer maxOccurs = getAsInteger(childDescr, Util.MAX_OCCURS);
                                            childrenTable.addLine(new String[] {prop.getName(), getAsString(childDescr, Util.MIN_OCCURS), maxOccurs == null ? "n/a"
                                                    : (maxOccurs == Integer.MAX_VALUE ? "unbounded" : maxOccurs.toString()) });
                                        } else {
                                            childrenTable.addLine(new String[] {prop.getName(), "n/a", "n/a" });
                                        }
                                    }
                                }

                                StringBuilder buf = null;
                                if (attrTable != null && !attrTable.isEmpty()) {
                                    buf = new StringBuilder();
                                    attrTable.append(buf, true);
                                }
                                if (childrenTable != null
                                        && !childrenTable.isEmpty()) {
                                    if (buf == null) {
                                        buf = new StringBuilder();
                                    } else {
                                        buf.append("\n\n");
                                    }
                                    childrenTable.append(buf, true);
                                }
                                if (buf != null) {
                                    cliCommandInvocation.getShell().out().println(buf.toString());
                                    cliCommandInvocation.getShell().out().flush();
                                }
                            }
                        } else {
                            throw new CommandFormatException("Result is not available for read-resource request: " + outcome);
                        }
                    } else {
                        throw new CommandFormatException("Failed to fetch attributes: " + outcome);
                    }
                } else {
                    throw new CommandFormatException("The result for attributes is not available: " + outcome);
                }
            }
        } else {
            throw new CommandFormatException("Failed to fetch the list of children: " + outcome);
        }

        if(names != null) {
            //printList(ctx, names, l.isPresent(parsedCmd));
            for(String item : names) {
                cliCommandInvocation.getShell().out().println(item);
            }
            cliCommandInvocation.getShell().out().flush();
        }


    }

    protected String getAsString(final ModelNode attrDescr, String name) {
        if(attrDescr == null) {
            return "n/a";
        }
        return attrDescr.has(name) ? attrDescr.get(name).asString() : "n/a";
    }

    protected Integer getAsInteger(final ModelNode attrDescr, String name) {
        if(attrDescr == null) {
            return null;
        }
        return attrDescr.has(name) ? attrDescr.get(name).asInt() : null;
    }

}
