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

import java.util.Arrays;
import java.util.List;
import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class SSLSecurityBuilder {

    private String sslContextName;
    private String keyManagerName;
    private String protocols;
    private final ModelNode composite = new ModelNode();
    private ServerSSLContext sslContext;

    public SSLSecurityBuilder() throws CommandException {
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
    }

    public ModelNode getRequest() {
        return composite;
    }

    public ModelNode getSteps() {
        return composite.get(Util.STEPS);
    }

    public ServerSSLContext getServerSSLContext() {
        return sslContext;
    }

    public SSLSecurityBuilder setSSLContextName(String sslContextName) {
        this.sslContextName = sslContextName;
        return this;
    }

    public SSLSecurityBuilder setKeyManagerName(String keyManagerName) {
        this.keyManagerName = keyManagerName;
        return this;
    }

    public SSLSecurityBuilder setProtocols(String protocols) {
        this.protocols = protocols;
        return this;
    }

    protected abstract KeyStore buildKeyStore(CommandContext ctx, ModelNode step) throws Exception;

    public void buildRequest(CommandContext ctx) throws Exception {
        KeyStore keyStore;
        KeyManager km;

        // First build the keyStore.
        final ModelNode steps = getSteps();
        keyStore = buildKeyStore(ctx, steps);
        // Then the key manager
        km = buildKeyManager(ctx, keyStore, steps);

        // Finally the SSLContext;
        sslContext = buildServerSSLContext(ctx, km, steps);
    }

    private KeyManager buildKeyManager(CommandContext ctx, KeyStore keyStore, ModelNode steps) throws Exception {
        if (keyManagerName == null) {
            keyManagerName = DefaultResourceNames.buildDefaultKeyManagerName(ctx, keyStore.getName());
        }
        String name = null;
        boolean exists = false;
        // Lookup for a matching key manager only if the keystore already exists.
        if (keyStore.exists()) {
            name = ElytronUtil.findMatchingKeyManager(ctx, keyStore, null, null);
        }
        if (name == null) {
            name = keyManagerName;
            steps.add(ElytronUtil.addKeyManager(ctx, keyStore, keyManagerName, null, null));
        } else {
            exists = true;
        }
        return new KeyManager(name, keyStore, exists);
    }

    private ServerSSLContext buildServerSSLContext(CommandContext ctx, KeyManager manager, ModelNode steps) throws Exception {
        if (sslContextName == null) {
            sslContextName = DefaultResourceNames.buildDefaultSSLContextName(ctx, manager.getKeyStore().getName());
        }
        List<String> lst;
        if (protocols == null) {
            lst = DefaultResourceNames.getDefaultProtocols(ctx);
        } else {
            lst = Arrays.asList(protocols.split(",+"));
        }
        String name = null;
        boolean exists = false;

        // Lookup for a matching sslcontext only if the keymanager already exists.
        if (manager.exists()) {
            name = ElytronUtil.findMatchingSSLContext(ctx, manager, false, false, null, lst);
        }

        if (name == null) {
            steps.add(ElytronUtil.addServerSSLContext(ctx, manager, false, false, null, lst, sslContextName));
            name = sslContextName;
        } else {
            exists = true;
        }
        return new ServerSSLContext(name, manager, exists);
    }

    public void exceptionOccured(CommandContext ctx, Exception ex) {

    }

}
