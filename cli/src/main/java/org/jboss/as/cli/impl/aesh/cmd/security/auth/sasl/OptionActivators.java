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

import java.util.Set;
import org.aesh.command.impl.internal.ParsedCommand;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOneOfOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.RejectOptionActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FILE_SYSTEM_REALM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_FILE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LOCAL_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LOCAL_SUPER_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_DIGEST_MD5;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_PLAIN;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_EXTERNAL_TRUST_STORE;

/**
 *
 * @author jdenise@redhat.com
 */
public class OptionActivators {

    public static class LocalJBOSSSuperUserActivator extends AbstractRejectOptionActivator {

        public LocalJBOSSSuperUserActivator() {
            super(OPT_LOCAL_USER, OPT_DIGEST_MD5, OPT_PLAIN, OPT_EXTERNAL_TRUST_STORE);
        }
    }

    public static class LocalJBOSSUserActivator extends AbstractRejectOptionActivator {

        public LocalJBOSSUserActivator() {
            super(OPT_LOCAL_SUPER_USER, OPT_DIGEST_MD5, OPT_PLAIN, OPT_EXTERNAL_TRUST_STORE);
        }
    }

    public static class DigestMD5Activator extends AbstractRejectOptionActivator {

        public DigestMD5Activator() {
            super(OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_PLAIN, OPT_EXTERNAL_TRUST_STORE);
        }
    }

    public static class MechanismsOrderActivator extends AbstractRejectOptionActivator {

        public MechanismsOrderActivator() {
            super(OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_PLAIN, OPT_EXTERNAL_TRUST_STORE, OPT_DIGEST_MD5);
        }
    }

    public static class PlainActivator extends AbstractRejectOptionActivator {

        public PlainActivator() {
            super(OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_DIGEST_MD5, OPT_EXTERNAL_TRUST_STORE);
        }
    }

    public static class ExternalTrustStoreActivator extends AbstractRejectOptionActivator {

        public ExternalTrustStoreActivator() {
            super(OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_DIGEST_MD5, OPT_PLAIN);
        }
    }

    public static class FileSystemRealmActivator extends AbstractDependOneOfOptionActivator implements RejectOptionActivator {
        private class Reject extends AbstractRejectOptionActivator {

            Reject() {
                super(OPT_USER_PROPERTIES_FILE);
            }
        }
        Reject reject = new Reject();
        public FileSystemRealmActivator() {
            super(OPT_DIGEST_MD5, OPT_PLAIN);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            if (!super.isActivated(processedCommand)) {
                return false;
            }
            return reject.isActivated(processedCommand);
        }

        @Override
        public Set<String> getRejected() {
            return reject.getRejected();
        }
    }

    public static class UserPropertiesFileActivator extends AbstractDependOneOfOptionActivator implements RejectOptionActivator {

        private class Reject extends AbstractRejectOptionActivator {

            Reject() {
                super(OPT_FILE_SYSTEM_REALM);
            }
        }
        Reject reject = new Reject();

        public UserPropertiesFileActivator() {
            super(OPT_DIGEST_MD5, OPT_PLAIN);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            if (!super.isActivated(processedCommand)) {
                return false;
            }
            return reject.isActivated(processedCommand);
        }

        @Override
        public Set<String> getRejected() {
            return reject.getRejected();
        }
    }

    public static class GroupPropertiesFileActivator extends AbstractDependOptionActivator {

        public GroupPropertiesFileActivator() {
            super(false, OPT_USER_PROPERTIES_FILE);
        }
    }

    public static class UserPropertiesRealmNameActivator extends AbstractDependOptionActivator {

        public UserPropertiesRealmNameActivator() {
            super(false, OPT_USER_PROPERTIES_FILE);
        }
    }

    public static class RelativeToActivator extends AbstractDependOptionActivator {

        public RelativeToActivator() {
            super(false, OPT_USER_PROPERTIES_FILE);
        }
    }

    public static class FileSystemRoleDecoderActivator extends AbstractDependOptionActivator {

        public FileSystemRoleDecoderActivator() {
            super(false, OPT_FILE_SYSTEM_REALM);
        }
    }

    public static class DependsOnMechanism extends AbstractDependOneOfOptionActivator {

        public DependsOnMechanism() {
            super(OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_DIGEST_MD5, OPT_PLAIN, OPT_EXTERNAL_TRUST_STORE);
        }
    }

    public static class NewRealmNameActivator extends AbstractDependOneOfOptionActivator implements DependOptionActivator {

        private class Depend extends AbstractDependOptionActivator {

            Depend() {
                super(false, OPT_USER_PROPERTIES_FILE);
            }
        }
        Depend depend = new Depend();

        public NewRealmNameActivator() {
            super(OPT_DIGEST_MD5, OPT_PLAIN);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            if (!super.isActivated(processedCommand)) {
                return false;
            }
            return depend.isActivated(processedCommand);
        }

        @Override
        public Set<String> getDependsOn() {
            return depend.getDependsOn();
        }
    }
}
