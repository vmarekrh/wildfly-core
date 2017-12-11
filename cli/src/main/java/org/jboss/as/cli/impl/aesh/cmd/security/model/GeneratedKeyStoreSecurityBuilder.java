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
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.File;
import java.util.List;
import java.util.UUID;
import org.aesh.command.CommandException;
import org.aesh.readline.Prompt;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class GeneratedKeyStoreSecurityBuilder extends SSLSecurityBuilder {

    private String dn;
    private String password;
    private String alias;
    private CLICommandInvocation commandInvocation;
    private String validity;
    private String keyStoreName;

    private static final String PLACE_HOLDER = "<need user input>";
    private String keyStoreFile;

    private final String defaultKeyStoreFile;

    private static final String KEY_ALG = "RSA";
    private static final int KEY_SIZE = 1024;

    public GeneratedKeyStoreSecurityBuilder(String defaultKeyStoreFile) throws CommandException {
        this.defaultKeyStoreFile = defaultKeyStoreFile;
    }

    public GeneratedKeyStoreSecurityBuilder setCommandInvocation(CLICommandInvocation commandInvocation) {
        this.commandInvocation = commandInvocation;
        return this;
    }

    @Override
    protected KeyStore buildKeyStore(CommandContext ctx, ModelNode step) throws Exception {
        if (commandInvocation == null) {
            keyStoreFile = PLACE_HOLDER;
            dn = PLACE_HOLDER;
            password = PLACE_HOLDER;
            alias = PLACE_HOLDER;
            validity = PLACE_HOLDER;
        } else {
            ctx.printLine("Please provide required pieces of information to generate key pair.");
        }
        String relativeTo = Util.JBOSS_SERVER_CONFIG_DIR;
        boolean ok = false;
        Long v = null;
        String certName = null;

        // First keystore file name
        while (keyStoreFile == null) {
            keyStoreFile = commandInvocation.inputLine(new Prompt("Key-store file name (default " + defaultKeyStoreFile + "): "));
            if (keyStoreFile != null && keyStoreFile.length() == 0) {
                keyStoreFile = defaultKeyStoreFile;
            }
            //Check that we are not going to corrupt existing key-stores that are referencing the exact same file.
            List<String> ksNames = ElytronUtil.findMatchingKeyStores(ctx, new File(keyStoreFile), relativeTo);
            if (!ksNames.isEmpty()) {
                throw new CommandException("Error, the file " + keyStoreFile + " is already referenced from " + ksNames
                        + " resources. Use " + SecurityCommand.formatOption(OPT_KEY_STORE_NAME) + " option or choose another file name.");
            }
        }
        certName = keyStoreFile + ".pem";

        // then password
        while (password == null || password.length() == 0) {
            password = commandInvocation.inputLine(new Prompt("Password: "));
        }

        // then prompt for dn
        while (dn == null || dn.length() == 0) {
            dn = commandInvocation.inputLine(new Prompt("Distinguished Name: "));
        }

        // then validity
        while (validity == null) {
            validity = commandInvocation.inputLine(new Prompt("Validity (in days, blank default): "));
            if (validity != null) {
                if (validity.length() == 0) {
                    v = null;
                } else {
                    try {
                        v = Long.parseLong(validity);
                    } catch (NumberFormatException e) {
                        ctx.printLine("Invalid number " + validity);
                        validity = null;
                    }
                }
            }
        }

        // then alias
        while (alias == null || alias.length() == 0) {
            alias = commandInvocation.inputLine(new Prompt("Alias: "));
        }

        if (commandInvocation != null) {
            String reply = null;
            while (reply == null) {
                ctx.printLine("\nKey-store creation options:");
                ctx.printLine("key store file: " + keyStoreFile + "\n"
                        + "distinguished name: " + dn + "\n"
                        + "password: " + password + "\n"
                        + "validity:" + (validity.length() == 0 ? "default" : validity) + "\n"
                        + "alias:" + alias);
                if (commandInvocation != null) {
                    ctx.printLine("Server keystore file " + keyStoreFile
                            + " and certificate file " + certName + " will be generated in server configuration directory.");
                }
                reply = commandInvocation.inputLine(new Prompt("Do you confirm (y or n) :"));
                if (reply != null && reply.equals("y")) {
                    ok = true;
                    break;
                } else if (reply != null && !reply.equals("n")) {
                    reply = null;
                }
            }
            if (!ok) {
                throw new CommandException("Command aborted.");
            }
        }

        String type = DefaultResourceNames.buildDefaultKeyStoreType(null, ctx);

        String id = UUID.randomUUID().toString();
        setKeyManagerName("key-manager-" + id);
        setSSLContextName("ssl-context" + id);

        keyStoreName = "key-store-" + id;
        ModelNode request = ElytronUtil.addKeyStore(ctx, keyStoreName, new File(keyStoreFile), relativeTo, password, type, false, null);
        // For now that is a workaround because we can't add and call operation in same composite.
        if (commandInvocation == null) { // echo-dmr
            step.add(request);
        } else {
            SecurityCommand.execute(ctx, request);
        }
        // Hard coded algorithm and key size.
        ModelNode request2 = ElytronUtil.generateKeyPair(ctx, keyStoreName, dn, alias, v, KEY_ALG, KEY_SIZE);
        step.add(request2);
        ModelNode request3 = ElytronUtil.storeKeyStore(ctx, keyStoreName);
        step.add(request3);
        ModelNode request4 = ElytronUtil.exportCertificate(ctx, keyStoreName, new File(certName), relativeTo, alias, true);
        step.add(request4);

        return new KeyStore(keyStoreName, password, alias, false);
    }

    @Override
    public void exceptionOccured(CommandContext ctx, Exception ex) {
        if (keyStoreName != null) {
            try {
                ModelNode req = ElytronUtil.removeKeyStore(ctx, keyStoreName);
                SecurityCommand.execute(ctx, req);
            } catch (Exception ex2) {
                ex.addSuppressed(ex2);
            }
        }
    }
}
