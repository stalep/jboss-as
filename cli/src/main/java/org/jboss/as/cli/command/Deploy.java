package org.jboss.as.cli.command;

import org.jboss.aesh.cl.Option;
import org.jboss.aesh.cl.OptionList;
//import org.jboss.aesh.cl.activation.OptionActivator;
import org.jboss.aesh.cl.completer.OptionCompleter;
import org.jboss.aesh.cl.validator.OptionValidator;
import org.jboss.aesh.cl.validator.OptionValidatorException;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandResult;
//import org.jboss.aesh.console.command.validator.ValidatorInvocation;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.activator.DomainOptionActivator;
import org.jboss.as.cli.command.helper.DeploymentHelper;
import org.jboss.as.cli.provider.CliCompleterInvocation;
import org.jboss.as.cli.provider.CliValidatorInvocationImpl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class Deploy extends DeploymentHelper implements Command<CliCommandInvocation> {

    @Option(shortName = 'f', hasValue = false,
            description = "If the deployment with the specified name already exists, "+
                     "by default, deploy will be aborted and the corresponding "+
                     "message will printed. Switch --force (or -f) will force the "+
                     "replacement of the existing deployment with the one "+
                     "specified in the command arguments.")
    private boolean force;

    @Option(description = "filesystem path to the application to deploy."+
            "If the deployment has to be added or replaced in the repository,"+
            " either the file_path or the --url argument is required."+
            " The path can be either absolute or relative to the current directory.")
    private File path;

    @Option(shortName = 'u',
            description = "URL at which the deployment content is available for "+
                     "upload to the deployment content repository. Note that the "+
                     "URL must be accessible from the target of the operation "+
                     "(i.e. the Domain Controller or standalone server). "+
                     "If the deployment has to be added or replaced in the "+
                     "repository, either the file_path or the --url argument is required. "+
                     "The path can be either absolute or relative to the current directory.")
    private URL url;

    @Option(shortName = 'n', completer = NameCompleter.class,
            description = "The unique name of the deployment. If the file path "+
                     "argument is specified the name argument is optional with "+
                     "the file name been the default value. If the file path "+
                     "argument isn't specified then the command is supposed to "+
                     "enable an already existing but disabled deployment, and in "+
                     "this case the name argument is required.")
    private String name;

    @Option(name = "runtimeName",
            description = "The runtime name for the deployment. This will "+
                     "form the basis for such things as default Java EE "+
                     "application and module names. This would typically be the "+
                     "same as --name, and if not specified the value used for "+
                     "--name will be used. In some cases users may wish to have "+
                     "two deployments with the same 'runtime-name' (e.g. two "+
                     "versions of \"example.war\") both available in the management "+
                     "configuration, in which case the deployments would need to "+
                     "have distinct 'name' values but would have the same "+
                     "\'runtime-name\'. Within an individual server, only one "+
                     "deployment with a given \'runtime-name\' can deployed. "+
                     "However, multiple deployments with the same 'runtime-name' "+
                     "can exist in the configuration, so long as only one is enabled.")
    private String runtimeName;

    @Option(overrideRequired = true,
            description = "In case none of the required arguments is specified the "+
                    "command will print all of the existing deployments in the "+
                    "repository. The presence of the -l switch will make the "+
                    "existing deployments printed one deployment per line, "+
                    "instead of in columns (the default).")
    private boolean l;

    @Option(hasValue = false,
            description = "indicates that the deployment has to be added to the repository disabled.")
    private boolean disabled;

    @Option(hasValue = false,
            description = "If this argument is not specified, the deployment content "+
                    "will be copied (i.e. uploaded) to the server's deployment "+
                    "repository before it is deployed. If the argument is "+
                    "present, the deployment content will remain at and be "+
                    "deployed directly from its original location specified with "+
                    "the file_path. "+
                    "NOTE: exploded deployments are supported only as unmanaged.")
    private boolean unmanaged;

    @OptionList(name = "server-groups", validator = ServerGroupsValidator.class,
            activator = DomainOptionActivator.class,
            description = "Comma separated list of server group names the deploy "+
                    "command should apply to. Either server-groups or "+
                    "all-server-groups is required in the domain mode. This "+
                    "argument is not applicable in the standalone mode.")
    private List<String> serverGroups;

    @Option(name = "all-server-groups",
            validator = AllServerGroupsValidator.class,
            activator = DomainOptionActivator.class,
            description = "indicates that deploy should apply to all the available "+
                        "server groups. Either server-groups or all-server-groups "+
                        "is required in domain mode. This argument is not "+
                        "applicable in the standalone mode.")
    private boolean allServerGroups;

    @OptionList(valueSeparator = ';',
            description = "A list of operation headers separated by a semicolon. For "+
                    "the list of supported headers, please, refer to the domain "+
                    "management documentation or use tab-completion.")
    private List<String> headers;

    @Option(defaultValue = "deploy.scr",
            description = "Optional, can appear only if the file_path points a cli "+
                    "archive. The value is the name of the script contained in a "+
                    "cli archive to execute. If not specified, defaults to "+
                    "\'deploy.scr\'. A cli archive is a zip archive containing "+
                    "script(s) as well as artifacts or applications to deploy. "+
                    "To be recognized as a cli archive, the extension of the "+
                    "archive file should be '.cli'. The deploy command will "+
                    "execute the script given by the --script argument. All paths "+
                    "in the scripts are relative to the root directory in the cli "+
                    "archive. The script is executed as a batch.")
    private String script;

    @Override
    public CommandResult execute(CliCommandInvocation commandInvocation) throws IOException, InterruptedException {
        if(l) {

            return CommandResult.SUCCESS;
        }


        return null;
    }

    private static class NameCompleter implements OptionCompleter<CliCompleterInvocation> {

        @Override
        public void complete(CliCompleterInvocation completerInvocation) {
            Deploy deploy = (Deploy) completerInvocation.getCommand();
            if(deploy.path != null || deploy.url != null)
                return;

            if(completerInvocation.getCommandContext().getModelControllerClient() != null) {
                List<String> deployments =
                        Util.getDeployments(completerInvocation.getCommandContext().getModelControllerClient());
                if(deployments.isEmpty()) {
                    return;
                }
                else {
                    if(completerInvocation.getGivenCompleteValue().isEmpty())
                        completerInvocation.addAllCompleterValues(deployments);
                    else
                        for(String name : deployments)
                            if(name.startsWith(completerInvocation.getGivenCompleteValue()))
                                completerInvocation.addCompleterValue(name);
                }
            }
        }
    }

    private static class AllServerGroupsValidator implements OptionValidator<CliValidatorInvocationImpl> {
        @Override
        public void validate(CliValidatorInvocationImpl validatorInvocation) throws OptionValidatorException {
            if(!validatorInvocation.getCommandContext().isDomainMode())
                throw new OptionValidatorException("--all-server-groups option must only be used in domain mode.");
        }
    }

    private static class ServerGroupsValidator implements OptionValidator<CliValidatorInvocationImpl> {
        @Override
        public void validate(CliValidatorInvocationImpl validatorInvocation) throws OptionValidatorException {
            if(!validatorInvocation.getCommandContext().isDomainMode())
                throw new OptionValidatorException("-server-groups option must only be used in domain mode.");
        }
    }

}
