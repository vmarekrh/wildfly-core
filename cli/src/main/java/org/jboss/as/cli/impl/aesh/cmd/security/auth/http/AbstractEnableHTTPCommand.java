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
package org.jboss.as.cli.impl.aesh.cmd.security.auth.http;

import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFile;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFilePathConverter;
import org.jboss.as.cli.impl.aesh.cmd.security.auth.AbstractEnableAuthenticationCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.MechanismConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthMechanism;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_DIGEST;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_BASIC;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FILE_SYSTEM_REALM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FORM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_GROUP_PROPERTIES_FILE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LOCAL_SUPER_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LOCAL_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_AUTH_FACTORY_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_SECURITY_DOMAIN_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_RELATIVE_TO;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_FILE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_ROLE_DECODER;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_CLIENT_CERT_TRUST_STORE;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-http-enable", description = "", activator = ConnectedActivator.class)
public abstract class AbstractEnableHTTPCommand extends AbstractEnableAuthenticationCommand {

    @Option(name = OPT_LOCAL_USER, hasValue = false, activator = OptionActivators.LocalJBOSSUserActivator.class)
    boolean localUser;

    @Option(name = OPT_LOCAL_SUPER_USER, hasValue = false, activator = OptionActivators.LocalJBOSSSuperUserActivator.class)
    boolean localSuperUser;

    @Option(name = OPT_FILE_SYSTEM_REALM, activator = OptionActivators.FileSystemRealmActivator.class,
            completer = OptionCompleters.FileSystemRealmCompleter.class)
    String fileSystemRealm;

    @Option(name = OPT_USER_ROLE_DECODER, activator = OptionActivators.FileSystemRoleDecoderActivator.class,
            completer = OptionCompleters.SimpleDecoderCompleter.class)
    String userRoleDecoder;

    @Option(name = OPT_USER_PROPERTIES_FILE, activator = OptionActivators.UserPropertiesFileActivator.class,
            converter = RelativeFilePathConverter.class, completer = FileOptionCompleter.class)
    RelativeFile userPropertiesFile;

    @Option(name = OPT_GROUP_PROPERTIES_FILE, activator = OptionActivators.GroupPropertiesFileActivator.class,
            converter = RelativeFilePathConverter.class, completer = FileOptionCompleter.class)
    RelativeFile groupPropertiesFile;

    @Option(name = OPT_USER_PROPERTIES_REALM_NAME, activator = OptionActivators.UserPropertiesRealmNameActivator.class)
    String userPropertiesRealmName;

    @Option(name = OPT_RELATIVE_TO, activator = OptionActivators.RelativeToActivator.class)
    String relativeTo;

    @Option(name = OPT_NO_RELOAD, hasValue = false, activator = OptionActivators.DependsOnMechanism.class)
    boolean noReload;

    @Option(name = OPT_NEW_SECURITY_DOMAIN_NAME, activator = OptionActivators.DependsOnMechanism.class)
    String newSecurityDomain;

    @Option(name = OPT_NEW_AUTH_FACTORY_NAME, activator = OptionActivators.DependsOnMechanism.class)
    String newAuthFactoryName;

    @Option(name = OPT_NEW_REALM_NAME, activator = OptionActivators.NewRealmNameActivator.class)
    String newRealmName;

    @Option(name = OPT_DIGEST, hasValue = false, activator = OptionActivators.DigestActivator.class)
    boolean digest;

    @Option(name = OPT_BASIC, hasValue = false, activator = OptionActivators.BasicActivator.class)
    boolean basic;

    @Option(name = OPT_FORM, activator = OptionActivators.FormActivator.class, hasValue = false)
    boolean form;

    @Option(name = OPT_CLIENT_CERT_TRUST_STORE, activator = OptionActivators.ClientCertActivator.class,
            completer = OptionCompleters.KeyStoreNameCompleter.class)
    String clientCertTrustStore;

    public AbstractEnableHTTPCommand() {
        super(AuthFactorySpec.HTTP);
    }

    @Override
    protected AuthMechanism buildAuthMechanism(CommandContext context)
            throws Exception {
        AuthMechanism mec = null;
        if (digest) {
            // Lookup the condiguration.
            MechanismConfiguration config = buildUserPasswordConfiguration(userPropertiesFile,
                    fileSystemRealm, userRoleDecoder, userPropertiesRealmName,
                    groupPropertiesFile, relativeTo);
            mec = new AuthMechanism(ElytronUtil.DIGEST_MECHANISM, config);
        }
        if (basic) {
            if (mec != null) {
                throwInvalidOptions();
            }
            // Lookup the configuration.
            MechanismConfiguration config = buildUserPasswordConfiguration(userPropertiesFile,
                    fileSystemRealm, userRoleDecoder, userPropertiesRealmName,
                    groupPropertiesFile, relativeTo);
            mec = new AuthMechanism(ElytronUtil.BASIC_MECHANISM, config);
        }
        if (form) {
            if (mec != null) {
                throwInvalidOptions();
            }
            // Lookup the configuration.
            MechanismConfiguration config = buildUserPasswordConfiguration(userPropertiesFile,
                    fileSystemRealm, userRoleDecoder, userPropertiesRealmName,
                    groupPropertiesFile, relativeTo);
            mec = new AuthMechanism(ElytronUtil.FORM_MECHANISM, config);
        }
        if (clientCertTrustStore != null) {
            if (mec != null) {
                throwInvalidOptions();
            }
            MechanismConfiguration config = buildExternalConfiguration(context, clientCertTrustStore);
            mec = new AuthMechanism(ElytronUtil.CLIENT_CERT_MECHANISM, config);
        }
        if (localUser) {
            if (mec != null) {
                throwInvalidOptions();
            }
            MechanismConfiguration config = buildLocalUserConfiguration(context, false);
            mec = new AuthMechanism(ElytronUtil.JBOSS_LOCAL_USER_MECHANISM, config);
        }
        if (localSuperUser) {
            if (mec != null) {
                throwInvalidOptions();
            }
            MechanismConfiguration config = buildLocalUserConfiguration(context, true);
            mec = new AuthMechanism(ElytronUtil.JBOSS_LOCAL_USER_MECHANISM, config);
        }
        return mec;
    }

    @Override
    protected boolean shouldReload() {
        return !noReload;
    }

    @Override
    protected void configureBuilder(AuthSecurityBuilder builder) {
        builder.setRealmName(newRealmName).
                setAuthFactoryName(newAuthFactoryName).setSecurityDomainName(newSecurityDomain);
    }
}
