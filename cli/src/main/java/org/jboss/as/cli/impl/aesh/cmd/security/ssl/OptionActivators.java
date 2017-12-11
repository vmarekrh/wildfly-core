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

import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.Util;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PATH;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MANAGEMENT_INTERFACE;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOneOfOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
public interface OptionActivators {

    public static class KeyStorePathActivator extends AbstractRejectOptionActivator {

        public KeyStorePathActivator() {
            super(OPT_KEY_STORE_NAME);
        }
    }

    public static class KeyStoreNameActivator extends AbstractRejectOptionActivator {

        public KeyStoreNameActivator() {
            super(OPT_KEY_STORE_PATH);
        }
    }

    public static class NewKeyStoreNameActivator extends AbstractDependOptionActivator {

        public NewKeyStoreNameActivator() {
            super(false, OPT_KEY_STORE_PATH);
        }
    }

    public static class NewSSLContextNameActivator extends AbstractDependOneOfOptionActivator {

        public NewSSLContextNameActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }

    public static class NewKeyManagerNameActivator extends AbstractDependOneOfOptionActivator {

        public NewKeyManagerNameActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }

    public static class ProtocolsActivator extends AbstractDependOneOfOptionActivator {

        public ProtocolsActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }

    public static class NoReloadActivator extends AbstractDependOneOfOptionActivator {

        public NoReloadActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }

    public static class NoOverrideSecurityRealmActivator extends AbstractDependOneOfOptionActivator {

        public NoOverrideSecurityRealmActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }


    public static class KeyStorePathDependentActivator extends AbstractDependOptionActivator {

        public KeyStorePathDependentActivator() {
            super(false, OPT_KEY_STORE_PATH);
        }
    }

    public static class SecureSocketBindingActivator extends AbstractDependOptionActivator {

        public SecureSocketBindingActivator() {
            super(false, OPT_MANAGEMENT_INTERFACE);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            ManagementEnableSSLCommand cmd = (ManagementEnableSSLCommand) processedCommand.command();
            if (cmd.managementInterface == null) {
                return false;
            }
            if (Util.HTTP_INTERFACE.equals(cmd.managementInterface)) {
                return true;
            }
            return false;
        }
    }
}
