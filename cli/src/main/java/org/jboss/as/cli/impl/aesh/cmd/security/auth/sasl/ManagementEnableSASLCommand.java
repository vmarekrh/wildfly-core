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
package org.jboss.as.cli.impl.aesh.cmd.security.auth.sasl;

import java.io.IOException;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MANAGEMENT_INTERFACE;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.DefaultResourceNames;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ManagementInterfaces;
import org.jboss.as.cli.operation.OperationFormatException;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "management-enable-sasl", description = "", activator = ConnectedActivator.class)
public class ManagementEnableSASLCommand extends AbstractEnableSASLCommand {

    @Option(name = OPT_MANAGEMENT_INTERFACE, activator = OptionActivators.DependsOnMechanism.class,
            completer = OptionCompleters.ManagementInterfaceCompleter.class)
    String managementInterface;

    @Override
    protected void secure(CommandContext ctx, AuthSecurityBuilder builder) throws Exception {
        ManagementInterfaces.enableSASL(managementInterface, builder, ctx);
    }

    @Override
    protected String getEnabledFactory(CommandContext ctx) throws IOException, OperationFormatException {
        return ManagementInterfaces.getManagementInterfaceSaslFactoryName(managementInterface, ctx);
    }

    @Override
    protected String getOOTBFactory(CommandContext ctx) throws Exception {
        return ElytronUtil.OOTB_MANAGEMENT_SASL_FACTORY;
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(ctx);
        }
        return "management " + managementInterface;
    }

}
