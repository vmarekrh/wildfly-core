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
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import org.jboss.as.cli.impl.aesh.cmd.RelativeFile;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFilePathConverter;
import java.io.File;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PASSWORD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PATH;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PATH_RELATIVE_TO;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_TYPE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_KEY_MANAGER_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_SSL_CONTEXT_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_PROTOCOLS;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.formatOption;
import org.jboss.as.cli.impl.aesh.cmd.security.model.GeneratedKeyStoreSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.KeyStoreNameSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.KeyStorePathSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.SSLSecurityBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-ssl-enable", description = "", activator = ConnectedActivator.class)
public abstract class AbstractEnableSSLCommand implements Command<CLICommandInvocation>, DMRCommand {

    @Option(name = OPT_KEY_STORE_NAME, completer = OptionCompleters.KeyStoreNameCompleter.class,
            activator = OptionActivators.KeyStoreNameActivator.class)
    String keystoreName;

    @Option(name = OPT_KEY_STORE_PATH, activator = OptionActivators.KeyStorePathActivator.class,
            converter = RelativeFilePathConverter.class, completer = FileOptionCompleter.class)
    RelativeFile keystorePath;

    @Option(name = OPT_KEY_STORE_PATH_RELATIVE_TO, activator = OptionActivators.KeyStorePathDependentActivator.class)
    String keystorePathRelativeTo;

    @Option(name = OPT_KEY_STORE_PASSWORD, activator = OptionActivators.KeyStorePathDependentActivator.class)
    String keystorePassword;

    @Option(name = OPT_KEY_STORE_TYPE, activator = OptionActivators.KeyStorePathDependentActivator.class,
            completer = OptionCompleters.KeyStoreTypeCompleter.class)
    String keyStoreType;

    @Option(name = OPT_NEW_KEY_MANAGER_NAME, activator = OptionActivators.NewKeyManagerNameActivator.class)
    String newKeyManagerName;

    @Option(name = OPT_NEW_SSL_CONTEXT_NAME, activator = OptionActivators.NewSSLContextNameActivator.class)
    String newSslContextName;

    @Option(name = OPT_NEW_KEY_STORE_NAME,
            activator = OptionActivators.NewKeyStoreNameActivator.class)
    String newKeystoreName;

    @Option(name = OPT_PROTOCOLS,
            activator = OptionActivators.ProtocolsActivator.class, completer = OptionCompleters.ProtocolsCompleter.class)
    String protocols;

    @Option(name = OPT_NO_RELOAD, hasValue = false, activator = OptionActivators.NoReloadActivator.class)
    boolean noReload;

    protected abstract void secure(CommandContext ctx, SSLSecurityBuilder ssl) throws CommandException;

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        try {
            return buildSecurityRequest(context, null).getRequest();
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage());
        }
    }

    private SSLSecurityBuilder buildSecurityRequest(CommandContext context, CLICommandInvocation commandInvocation) throws Exception {
        SSLSecurityBuilder builder = validateOptions();
        if (builder instanceof GeneratedKeyStoreSecurityBuilder) {
            ((GeneratedKeyStoreSecurityBuilder) builder).setCommandInvocation(commandInvocation);
        }
        builder.buildRequest(context);
        secure(context, builder);
        return builder;
    }

    protected abstract boolean isSSLEnabled(CommandContext ctx) throws Exception;

    protected abstract String getTarget(CommandContext ctx);

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        String target = getTarget(ctx);
        try {
            if (isSSLEnabled(ctx)) {
                throw new CommandException("SSL is already enabled for " + target);
            }
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage(), ex);
        }

        SSLSecurityBuilder builder;
        try {
            builder = buildSecurityRequest(ctx, commandInvocation);
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }
        try {
            SecurityCommand.execute(ctx, builder.getRequest(), noReload);
        } catch (CommandException ex) {
            builder.exceptionOccured(ctx, ex);
            throw ex;
        }
        commandInvocation.getCommandContext().printLine("SSL enabled for " + target);
        commandInvocation.getCommandContext().printLine("ssl-context is " + builder.getServerSSLContext().getName());
        commandInvocation.getCommandContext().printLine("key-manager is " + builder.getServerSSLContext().getKeyManager().getName());
        commandInvocation.getCommandContext().printLine("key-store   is " + builder.getServerSSLContext().getKeyManager().getKeyStore().getName());

        return CommandResult.SUCCESS;
    }

    abstract String getDefaultKeyStoreFileName();

    private SSLSecurityBuilder validateOptions() throws CommandException {
        if (keystoreName != null && keystorePath != null) {
            throw new CommandException(formatOption(OPT_KEY_STORE_NAME) + " or " + formatOption(OPT_KEY_STORE_PATH) + " can't both be set");
        }
        SSLSecurityBuilder builder = null;

        if (keystorePath == null && keystoreName == null) { // Interactive fully generated keystore.
            builder = new GeneratedKeyStoreSecurityBuilder(getDefaultKeyStoreFileName());
        } else if (keystorePath != null) {
            File path;
            if (keystorePathRelativeTo != null) {
                path = new File(keystorePath.getOriginalPath());
            } else {
                path = keystorePath;
            }
            KeyStorePathSecurityBuilder kspBuilder = new KeyStorePathSecurityBuilder(path, keystorePassword);
            kspBuilder.setRelativeTo(keystorePathRelativeTo).setType(keyStoreType).
                    setName(newKeystoreName).setProtocols(protocols);
            builder = kspBuilder;
        } else {
            builder = new KeyStoreNameSecurityBuilder(keystoreName);
        }
        return builder;
    }
}
