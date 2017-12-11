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

import java.util.Set;
import org.aesh.command.impl.internal.ParsedCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_BASIC;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_DIGEST;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOneOfOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.RejectOptionActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FILE_SYSTEM_REALM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_FILE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FORM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LOCAL_SUPER_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LOCAL_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_CLIENT_CERT_TRUST_STORE;

/**
 *
 * @author jdenise@redhat.com
 */
public class OptionActivators {

    public static class LocalJBOSSSuperUserActivator extends AbstractRejectOptionActivator {

        public LocalJBOSSSuperUserActivator() {
            super(OPT_LOCAL_USER, OPT_DIGEST, OPT_BASIC, OPT_FORM, OPT_CLIENT_CERT_TRUST_STORE);
        }
    }

    public static class LocalJBOSSUserActivator extends AbstractRejectOptionActivator {

        public LocalJBOSSUserActivator() {
            super(OPT_LOCAL_SUPER_USER, OPT_DIGEST, OPT_BASIC, OPT_FORM, OPT_CLIENT_CERT_TRUST_STORE);
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
            super(OPT_DIGEST, OPT_BASIC, OPT_FORM);
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
            super(OPT_DIGEST, OPT_BASIC, OPT_FORM);
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
            super(OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_DIGEST, OPT_BASIC, OPT_FORM, OPT_CLIENT_CERT_TRUST_STORE);
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
            super(OPT_DIGEST, OPT_BASIC, OPT_FORM);
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

    public static class DigestActivator extends AbstractRejectOptionActivator {

        public DigestActivator() {
            super(OPT_BASIC, OPT_FORM, OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_CLIENT_CERT_TRUST_STORE);
        }
    }

    public static class BasicActivator extends AbstractRejectOptionActivator {

        public BasicActivator() {
            super(OPT_DIGEST, OPT_FORM, OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_CLIENT_CERT_TRUST_STORE);
        }
    }

    public static class FormActivator extends AbstractRejectOptionActivator {

        public FormActivator() {
            super(OPT_DIGEST, OPT_BASIC, OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_CLIENT_CERT_TRUST_STORE);
        }
    }

    public static class ClientCertActivator extends AbstractRejectOptionActivator {

        public ClientCertActivator() {
            super(OPT_DIGEST, OPT_BASIC, OPT_LOCAL_SUPER_USER, OPT_LOCAL_USER, OPT_FORM);
        }
    }

}
