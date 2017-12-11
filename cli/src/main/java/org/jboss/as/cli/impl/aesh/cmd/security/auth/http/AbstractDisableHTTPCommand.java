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

import java.util.HashSet;
import java.util.Set;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.auth.AbstractDisableAuthenticationCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_DIGEST;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_BASIC;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FORM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_CLIENT_CERT_TRUST_STORE;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-http-disable", description = "", activator = ConnectedActivator.class)
public abstract class AbstractDisableHTTPCommand extends AbstractDisableAuthenticationCommand {

    @Option(name = OPT_DIGEST, hasValue = false)
    boolean digest;

    @Option(name = OPT_BASIC, hasValue = false)
    boolean basic;

    @Option(name = OPT_FORM, hasValue = false)
    boolean form;

    @Option(name = OPT_CLIENT_CERT_TRUST_STORE, hasValue = false)
    boolean clientCert;

    public AbstractDisableHTTPCommand(AuthFactorySpec factorySpec) {
        super(factorySpec);
    }

    @Override
    public Set<String> getMechanisms() {
        Set<String> toRemove = new HashSet<>();

        if (basic) {
            toRemove.add(ElytronUtil.BASIC_MECHANISM);
        }
        if (digest) {
            toRemove.add(ElytronUtil.DIGEST_MECHANISM);
        }
        if (form) {
            toRemove.add(ElytronUtil.FORM_MECHANISM);
        }
        if (clientCert) {
            toRemove.add(ElytronUtil.CLIENT_CERT_MECHANISM);
        }
        return toRemove;
    }
}
