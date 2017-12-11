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

import java.util.HashSet;
import java.util.Set;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.auth.AbstractDisableAuthenticationCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_DIGEST_MD5;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_PLAIN;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_EXTERNAL_TRUST_STORE;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-sasl-disable", description = "", activator = ConnectedActivator.class)
public abstract class AbstractDisableSASLCommand extends AbstractDisableAuthenticationCommand {

    @Option(name = OPT_DIGEST_MD5, hasValue = false)
    boolean digestMD5;

    @Option(name = OPT_PLAIN, hasValue = false)
    boolean plain;

    @Option(name = OPT_EXTERNAL_TRUST_STORE, hasValue = false)
    boolean externalTrustStore;

    public AbstractDisableSASLCommand(AuthFactorySpec factorySpec) {
        super(factorySpec);
    }

    @Override
    public Set<String> getMechanisms() {
        Set<String> toRemove = new HashSet<>();
        if (plain) {
            toRemove.add(ElytronUtil.PLAIN_MECHANISM);
        }
        if (digestMD5) {
            toRemove.add(ElytronUtil.DIGEST_MD5_MECHANISM);
        }
        if (externalTrustStore) {
            toRemove.add(ElytronUtil.EXTERNAL_MECHANISM);
        }
        return toRemove;
    }
}
