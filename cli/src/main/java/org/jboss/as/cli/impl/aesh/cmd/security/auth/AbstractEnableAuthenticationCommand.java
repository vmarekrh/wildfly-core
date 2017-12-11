/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import java.io.IOException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.FileSystemRealmConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.LocalUserConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.MechanismConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.PropertiesRealmConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFile;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_ROLE_DECODER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.formatOption;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactory;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthMechanism;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.TrustStoreConfiguration;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-enable", description = "", activator = ConnectedActivator.class)
public abstract class AbstractEnableAuthenticationCommand implements Command<CLICommandInvocation>, DMRCommand {

    private final AuthFactorySpec factorySpec;
    protected AbstractEnableAuthenticationCommand(AuthFactorySpec factorySpec) {
        this.factorySpec = factorySpec;
    }

    public AuthFactorySpec getFactorySpec() {
        return factorySpec;
    }
    protected abstract void secure(CommandContext ctx, AuthSecurityBuilder builder) throws Exception;

    protected abstract AuthMechanism buildAuthMechanism(CommandContext context) throws Exception;

    protected abstract String getOOTBFactory(CommandContext ctx) throws Exception;

    protected abstract String getSecuredEndpoint(CommandContext ctx);

    protected abstract String getEnabledFactory(CommandContext ctx) throws Exception;

    protected abstract boolean shouldReload();

    protected abstract void configureBuilder(AuthSecurityBuilder builder);

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        AuthSecurityBuilder builder;
        try {
            builder = buildSecurityRequest(ctx);
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }
        if (!builder.isEmpty()) {
            SecurityCommand.execute(ctx, builder.getRequest(), !shouldReload());
            commandInvocation.getCommandContext().printLine("Command success.");
            commandInvocation.getCommandContext().printLine("Authentication configured for "
                    + getSecuredEndpoint(commandInvocation.getCommandContext()));
            commandInvocation.getCommandContext().printLine(factorySpec.getName()
                    + " authentication-factory=" + builder.getAuthFactory().getName());
            commandInvocation.getCommandContext().printLine("security-domain="
                    + builder.getAuthFactory().getSecurityDomain().getName());
        } else {
            commandInvocation.getCommandContext().
                    printLine("Authentication is already enabled for " + getSecuredEndpoint(commandInvocation.getCommandContext()));
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        try {
            return buildSecurityRequest(context).getRequest();
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage());
        }
    }

    private AuthSecurityBuilder buildSecurityRequest(CommandContext context) throws Exception {
        AuthSecurityBuilder builder = buildSecurityBuilder(context);
        //OOTB
        if (builder == null) {
            String factoryName = getOOTBFactory(context);
            AuthFactory factory = ElytronUtil.getAuthFactory(factoryName, getFactorySpec(), context);
            if (factory == null) {
                throw new Exception("Can't enable " + factorySpec.getName() + " authentication, "
                        + factoryName + " doesn't exist");
            }
            builder = new AuthSecurityBuilder(factory);
        }
        builder.buildRequest(context);
        if (!builder.isFactoryAlreadySet()) {
            secure(context, builder);
        }
        return builder;
    }

    private AuthSecurityBuilder buildSecurityBuilder(CommandContext context) throws Exception {
        AuthMechanism mec = buildAuthMechanism(context);
        if (mec != null) {
            return buildSecurityBuilder(context, mec);
        }
        return null;
    }

    private AuthSecurityBuilder buildSecurityBuilder(CommandContext ctx, AuthMechanism mec) throws Exception {
        String existingFactory = getEnabledFactory(ctx);
        AuthSecurityBuilder builder = new AuthSecurityBuilder(mec, getFactorySpec());
        builder.setActiveFactoryName(existingFactory);
        configureBuilder(builder);
        return builder;
    }

    protected MechanismConfiguration buildLocalUserConfiguration(CommandContext ctx,
            boolean superUser) throws CommandException, IOException, OperationFormatException {
        if (!ElytronUtil.localUserExists(ctx)) {
            throw new CommandException("Can't configure 'local' user, no such identity.");
        }
        return new LocalUserConfiguration(superUser);
    }

    public static void throwInvalidOptions() throws CommandException {
        throw new CommandException("You must only set a single mechanism.");
    }

    protected static MechanismConfiguration buildExternalConfiguration(CommandContext ctx, String externalTrustStore) throws CommandException, IOException, OperationFormatException {
        if (!ElytronUtil.keyStoreExists(ctx, externalTrustStore)) {
            throw new CommandException("Can't configure 'certificate' authentication, no trustore " + externalTrustStore);
        }
        return new TrustStoreConfiguration(externalTrustStore);
    }

    protected static MechanismConfiguration buildUserPasswordConfiguration(RelativeFile userPropertiesFile,
            String fileSystemRealm, String userRoleDecoder, String userPropertiesRealmName, RelativeFile groupPropertiesFile, String relativeTo) throws CommandException, IOException {
        if (userPropertiesFile == null && fileSystemRealm == null) {
            throw new CommandException("A properties file or a filesystem-realm name must be provided");
        }

        if (userPropertiesFile != null && fileSystemRealm != null) {
            throw new CommandException("A properties file or a filesystem-realm name must be provided");
        }
        if (userPropertiesFile != null) {
            if (userPropertiesRealmName == null) {
                throw new CommandException(formatOption(OPT_USER_PROPERTIES_REALM_NAME) + " must be set when using a user properties file");
            }
            PropertiesRealmConfiguration config = new PropertiesRealmConfiguration(userPropertiesRealmName,
                    userPropertiesFile,
                    groupPropertiesFile,
                    relativeTo);
            return config;
        } else {
            if (userRoleDecoder == null) {
                throw new CommandException(formatOption(OPT_USER_ROLE_DECODER) + " must be set when using a filesystem realm");
            }
            FileSystemRealmConfiguration config = new FileSystemRealmConfiguration(fileSystemRealm, userRoleDecoder);
            return config;
        }
    }
}
