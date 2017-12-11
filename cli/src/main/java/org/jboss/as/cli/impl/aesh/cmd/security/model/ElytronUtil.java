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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class ElytronUtil {

    public static final String PLAIN_MECHANISM = "PLAIN";
    public static final String DIGEST_MD5_MECHANISM = "DIGEST-MD5";
    public static final String EXTERNAL_MECHANISM = "EXTERNAL";
    public static final String JBOSS_LOCAL_USER_MECHANISM = "JBOSS-LOCAL-USER";

    public static final String BASIC_MECHANISM = "BASIC";
    public static final String DIGEST_MECHANISM = "DIGEST";
    public static final String FORM_MECHANISM = "FORM";
    public static final String CLIENT_CERT_MECHANISM = "CLIENT-CERT";

    public static final String JKS = "JKS";
    public static final String TLS_V1_2 = "TLSv1.2";
    public static String OOTB_MANAGEMENT_SASL_FACTORY = "management-sasl-authentication";
    public static String OOTB_MANAGEMENT_HTTP_FACTORY = "management-http-authentication";
    public static String OOTB_APPLICATION_HTTP_FACTORY = "application-http-authentication";

    private ElytronUtil() {
    }

    static String retrieveKeyStorePassword(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.addProperty(Util.NAME, Util.CREDENTIAL_REFERENCE);
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        String password = null;
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                if (res.hasDefined(Util.CLEAR_TEXT)) {
                    password = res.get(Util.CLEAR_TEXT).asString();
                }
            }
        }
        return password;
    }

    static String findMatchingKeyStore(CommandContext ctx, File path, String relativeTo, String password, String type, Boolean required, String alias) throws OperationFormatException, IOException {
        ModelNode resource = buildKeyStoreResource(path, relativeTo, password, type, required, alias);
        List<String> names = findMatchingResources(ctx, Util.KEY_STORE, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    static List<String> findMatchingKeyStores(CommandContext ctx, File path, String relativeTo) throws OperationFormatException, IOException {
        ModelNode resource = buildKeyStoreResource(path, relativeTo, null, null, null, null);
        return findMatchingResources(ctx, Util.KEY_STORE, resource);
    }

    private static ModelNode buildKeyStoreResource(File path, String relativeTo,
            String password, String type, Boolean required, String alias) throws IOException {
        ModelNode localKS = new ModelNode();
        if (path != null) {
            localKS.get(Util.PATH).set(path.getPath());
        }
        if (relativeTo != null) {
            localKS.get(Util.RELATIVE_TO).set(relativeTo);
        } else {
            localKS.get(Util.RELATIVE_TO);
        }
        if (password != null) {
            localKS.get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(password));
        }
        if (type != null) {
            localKS.get(Util.TYPE).set(type);
        }
        if (required != null) {
            localKS.get(Util.REQUIRED).set(required);
        }
        if (alias != null) {
            localKS.get(Util.ALIAS_FILTER, alias);
        } else {
            localKS.get(Util.ALIAS_FILTER);
        }
        return localKS;
    }

    public static String findMatchingUsersPropertiesRealm(CommandContext ctx,
            PropertiesRealmConfiguration config) throws Exception {
        ModelNode resource = buildRealmResource(config);
        List<String> names = findMatchingResources(ctx, Util.PROPERTIES_REALM, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    public static AuthFactory findMatchingAuthFactory(AuthMechanism newMechanism,
            AuthFactorySpec spec, CommandContext ctx) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, spec.getResourceType());
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        AuthFactory factory = null;
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                for (String ksName : res.keys()) {
                    ModelNode ks = res.get(ksName);
                    AuthFactory fact = getAuthFactory(ks, ksName, spec, ctx);
                    List<AuthMechanism> mecs = fact.getMechanisms();
                    if (mecs.isEmpty() || mecs.size() > 1) {
                        continue;
                    }
                    AuthMechanism mec = mecs.get(0);
                    // Only compare, type, realmName and realmMapper.
                    if (newMechanism.getType().equals(mec.getType())) {
                        if (newMechanism.getConfig().getRealmMapper() == null) {
                            if (Objects.equals(newMechanism.getConfig().getRealmName(),
                                    mec.getConfig().getRealmName())) {
                                factory = fact;
                                break;
                            }
                        } else {
                            if (Objects.equals(newMechanism.getConfig().getRealmMapper(),
                                    mec.getConfig().getRealmMapper())) {
                                factory = fact;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return factory;
    }

    private static ModelNode buildRealmResource(PropertiesRealmConfiguration config) throws Exception {
        ModelNode localRealm = new ModelNode();
        localRealm.get(Util.GROUPS_ATTRIBUTE).set(Util.GROUPS);
        if (config.getGroupPropertiesFile() != null) {
            localRealm.get(Util.GROUPS_PROPERTIES).set(buildGroupsResource(config));
        }
        localRealm.get(Util.USERS_PROPERTIES).set(buildUsersResource(config));
        return localRealm;
    }

    public static ModelNode addUsersPropertiesRealm(CommandContext ctx, String realmName,
            PropertiesRealmConfiguration config) throws Exception {
        ModelNode mn = buildRealmResource(config);
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.PROPERTIES_REALM, realmName);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    private static ModelNode buildKeyManagerResource(KeyStore keyStore, String alias, String algorithm) {
        ModelNode localKM = new ModelNode();
        localKM.get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(keyStore.getPassword()));
        localKM.get(Util.KEY_STORE).set(keyStore.getName());
        if (alias != null) {
            localKM.get(Util.ALIAS_FILTER, alias);
        } else {
            localKM.get(Util.ALIAS_FILTER);
        }
        if (algorithm != null) {
            localKM.get(Util.ALGORITHM, algorithm);
        } else {
            localKM.get(Util.ALGORITHM);
        }
        return localKM;
    }

    private static ModelNode buildServerSSLContextResource(KeyManager keyManager,
            boolean want, boolean need, String trustManager, List<String> protocols) {
        ModelNode sslCtx = new ModelNode();
        sslCtx.get(Util.KEY_MANAGER).set(keyManager.getName());
        sslCtx.get(Util.WANT_CLIENT_AUTH).set(want);
        sslCtx.get(Util.NEED_CLIENT_AUTH).set(need);
        if (trustManager != null) {
            sslCtx.get(Util.TRUST_MANAGER).set(trustManager);
        } else {
            sslCtx.get(Util.TRUST_MANAGER);
        }
        if (protocols != null) {
            ModelNode protocolsNode = sslCtx.get(Util.PROTOCOLS);
            for (String p : protocols) {
                protocolsNode.add(p);
            }
        } else {
            sslCtx.get(Util.PROTOCOLS);
        }
        return sslCtx;
    }

    private static ModelNode buildSecurityDomainResource(Realm realm) {
        ModelNode sd = new ModelNode();
        sd.get(Util.REALMS).add(buildRealmResource(realm));
        sd.get(Util.DEFAULT_REALM).set(realm.getName());
        sd.get(Util.PERMISSION_MAPPER).set(Util.DEFAULT_PERMISSION_MAPPER);

        return sd;
    }

    private static ModelNode buildAuthFactoryResource(SecurityDomain domain, AuthFactorySpec spec) {
        ModelNode sd = new ModelNode();
        sd.get(spec.getServerType()).set(spec.getServerValue());
        sd.get(Util.SECURITY_DOMAIN).set(domain.getName());
        return sd;
    }

    private static List<String> findMatchingResources(CommandContext ctx, String type,
            ModelNode resource) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, type);
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        List<String> matches = new ArrayList<>();
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                for (String ksName : res.keys()) {
                    ModelNode ks = res.get(ksName);
                    List<String> meaninglessKeys = new ArrayList<>();
                    for (String k : ks.keys()) {
                        if (!resource.keys().contains(k)) {
                            meaninglessKeys.add(k);
                        }
                    }
                    for (String s : meaninglessKeys) {
                        ks.remove(s);
                    }
                    if (resource.equals(ks)) {
                        matches.add(ksName);
                    }
                }
            }
        }
        return matches;
    }

    static String findMatchingKeyManager(CommandContext ctx, KeyStore keyStore,
            String alias, String algorithm) throws OperationFormatException, IOException {
        ModelNode resource = buildKeyManagerResource(keyStore, alias, algorithm);
        List<String> names = findMatchingResources(ctx, Util.KEY_MANAGER, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    static String findMatchingSSLContext(CommandContext ctx, KeyManager manager,
            boolean want, boolean need, String trustManager, List<String> protocols) throws OperationFormatException, IOException {
        ModelNode resource = buildServerSSLContextResource(manager, want, need, trustManager, protocols);
        List<String> names = findMatchingResources(ctx, Util.SERVER_SSL_CONTEXT, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    public static List<String> getAllowedProtocols(CommandContext commandContext) {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE_DESCRIPTION);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SERVER_SSL_CONTEXT, "?");

        try {
            final ModelNode outcome = commandContext.getModelControllerClient().execute(builder.buildRequest());
            if (Util.isSuccess(outcome)) {
                if (outcome.hasDefined(Util.RESULT)) {
                    ModelNode res = outcome.get(Util.RESULT);
                    if (res.hasDefined(Util.ATTRIBUTES)) {
                        ModelNode attributes = res.get(Util.ATTRIBUTES);
                        if (attributes.hasDefined(Util.PROTOCOLS)) {
                            ModelNode protocols = attributes.get(Util.PROTOCOLS);
                            if (protocols.hasDefined(Util.ALLOWED)) {
                                List<ModelNode> nodeList = protocols.get(Util.ALLOWED).asList();
                                if (nodeList.isEmpty()) {
                                    return Collections.emptyList();
                                }
                                List<String> list = new ArrayList<>(nodeList.size());
                                for (ModelNode node : nodeList) {
                                    list.add(node.asString());
                                }
                                return list;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    static ModelNode addKeyStore(CommandContext ctx, String name, File path,
            String relativeTo, String password, String type, Boolean required, String alias) throws Exception {
        ModelNode mn = buildKeyStoreResource(path, relativeTo, password, type, required, alias);
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    static ModelNode generateKeyPair(CommandContext ctx, String name, String dn, String alias, Long validity, String keyAlg, int keySize) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.GENERATE_KEY_PAIR);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.getModelNode().get(Util.DISTINGUISHED_NAME).set(dn);
        builder.getModelNode().get(Util.ALGORITHM).set(keyAlg);
        builder.getModelNode().get(Util.KEY_SIZE).set(keySize);
        builder.getModelNode().get(Util.ALIAS).set(alias);
        if (validity != null) {
            builder.getModelNode().get(Util.VALIDITY).set(validity);
        }
        return builder.buildRequest();
    }

    static ModelNode storeKeyStore(CommandContext ctx, String name) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.STORE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        return builder.buildRequest();
    }

    static ModelNode removeKeyStore(CommandContext ctx, String name) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.REMOVE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        return builder.buildRequest();
    }

    static ModelNode exportCertificate(CommandContext ctx, String name, File path, String relativeTo, String alias, boolean pem) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.EXPORT_CERTIFICATE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.getModelNode().get(Util.PATH).set(path.getPath());
        builder.getModelNode().get(Util.ALIAS).set(alias);
        if (relativeTo != null) {
            builder.getModelNode().get(Util.RELATIVE_TO).set(relativeTo);
        }
        builder.getModelNode().get(Util.PEM).set(pem);
        return builder.buildRequest();
    }

    static ModelNode addKeyManager(CommandContext ctx, KeyStore keyStore, String name, String alias, String algorithm) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_MANAGER, name);
        ModelNode mn = buildKeyManagerResource(keyStore, alias, algorithm);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        builder.getModelNode().get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(keyStore.getPassword()));
        return builder.buildRequest();
    }

    static ModelNode addServerSSLContext(CommandContext ctx, KeyManager keyManager,
            boolean want, boolean need, String trustManager, List<String> protocols, String name) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SERVER_SSL_CONTEXT, name);
        ModelNode mn = buildServerSSLContextResource(keyManager, want, need, trustManager, protocols);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    private static ModelNode buildCredentialReferences(String password) {
        ModelNode mn = new ModelNode();
        mn.get(Util.CLEAR_TEXT).set(password);
        return mn;
    }

    private static ModelNode buildRealmResource(Realm realm) {
        ModelNode mn = new ModelNode();
        mn.get(Util.REALM).set(realm.getName());
        if (realm.getConfig().getRoleDecoder() != null) {
            mn.get(Util.ROLE_DECODER).set(realm.getConfig().getRoleDecoder());
        }
        if (realm.getConfig().getRoleMapper() != null) {
            mn.get(Util.ROLE_MAPPER).set(realm.getConfig().getRoleMapper());
        }
        return mn;
    }

    private static ModelNode buildMechanismResource(AuthMechanism mechanism) {
        ModelNode mn = new ModelNode();
        mn.get(Util.MECHANISM_NAME).set(mechanism.getType());
        if (mechanism.getConfig().getRealmMapper() != null) {
            mn.get(Util.REALM_MAPPER).set(mechanism.getConfig().getRealmMapper());
        } else {
            ModelNode realmConfig = new ModelNode();
            realmConfig.get(Util.REALM_NAME).set(mechanism.getConfig().getRealmName());
            mn.get(Util.MECHANISM_REALM_CONFIGURATIONS).add(realmConfig);
        }
        return mn;
    }

    private static ModelNode buildGroupsResource(PropertiesRealmConfiguration config) throws IOException {
        ModelNode mn = new ModelNode();
        mn.get(Util.PATH).set(config.getGroupPropertiesFile());
        if (config.getRelativeTo() != null) {
            mn.get(Util.RELATIVE_TO).set(config.getRelativeTo());
        }
        return mn;
    }

    private static ModelNode buildUsersResource(PropertiesRealmConfiguration config) throws IOException {
        ModelNode mn = new ModelNode();
        mn.get(Util.PATH).set(config.getUserPropertiesFile());
        if (config.getRelativeTo() != null) {
            mn.get(Util.RELATIVE_TO).set(config.getRelativeTo());
        }
        mn.get(Util.DIGEST_REALM_NAME).set(config.getRealmName());
        return mn;
    }

    static boolean keyManagerExists(CommandContext ctx, String name) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_MANAGER, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean keyStoreExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean serverSSLContextExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SERVER_SSL_CONTEXT, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean serverPropertiesRealmExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.PROPERTIES_REALM, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean securityDomainExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SECURITY_DOMAIN, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean factoryExists(CommandContext ctx, String name, AuthFactorySpec spec) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(spec.getResourceType(), name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static List<String> getKeyStoreNames(ModelControllerClient client) {
        return getNames(client, Util.KEY_STORE);
    }

    public static List<String> getFileSystemRealmNames(ModelControllerClient client) {
        return getNames(client, Util.FILESYSTEM_REALM);
    }

    public static List<String> getSimpleDecoderNames(ModelControllerClient client) {
        return getNames(client, Util.SIMPLE_ROLE_DECODER);
    }

    public static List<String> getSSLContextNames(ModelControllerClient client) {
        return getNames(client, Util.SERVER_SSL_CONTEXT);
    }

    private static List<String> getNames(ModelControllerClient client, String type) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addProperty(Util.CHILD_TYPE, type);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (Util.isSuccess(outcome)) {
                return Util.getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static ModelNode getAuthFactoryResource(String authFactory, AuthFactorySpec spec, CommandContext ctx) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(spec.getResourceType(), authFactory);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                return outcome.get(Util.RESULT);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static ModelNode getSecurityDomainResource(SecurityDomain domain, CommandContext ctx) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(Util.SECURITY_DOMAIN, domain.getName());
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                return outcome.get(Util.RESULT);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static AuthFactory getAuthFactory(String authFactory, AuthFactorySpec spec, CommandContext ctx) {
        ModelNode mn = getAuthFactoryResource(authFactory, spec, ctx);
        return getAuthFactory(mn, authFactory, spec, ctx);
    }

    public static AuthFactory getAuthFactory(ModelNode mn, String authFactory, AuthFactorySpec spec, CommandContext ctx) {
        AuthFactory factory = null;
        if (mn != null) {
            SecurityDomain sc = new SecurityDomain(mn.get(Util.SECURITY_DOMAIN).asString());
            factory = new AuthFactory(authFactory, sc, spec);
            if (mn.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
                ModelNode lst = mn.get(Util.MECHANISM_CONFIGURATIONS);
                for (ModelNode m : lst.asList()) {
                    String name = m.get(Util.MECHANISM_NAME).asString();
                    String realmMapper = null;
                    String realmName = null;
                    if (m.hasDefined(Util.REALM_MAPPER)) {
                        realmMapper = m.get(Util.REALM_MAPPER).asString();
                    }
                    // XXX This could be evolved with new exposed attributes
                    if (m.hasDefined(Util.MECHANISM_REALM_CONFIGURATIONS)) {
                        ModelNode config = m.get(Util.MECHANISM_REALM_CONFIGURATIONS);
                        for (ModelNode c : config.asList()) {
                            if (c.hasDefined(Util.REALM_NAME)) {
                                realmName = c.get(Util.REALM_NAME).asString();
                                break;
                            }
                        }
                    }
                    String finalRealmName = realmName;
                    String finalRealmMapper = realmMapper;
                    AuthMechanism mec = new AuthMechanism(name, new MechanismConfiguration() {
                        @Override
                        public String getRealmName() {
                            return finalRealmName;
                        }

                        @Override
                        public String getRoleDecoder() {
                            return null;
                        }

                        @Override
                        public String getRoleMapper() {
                            return null;
                        }

                        @Override
                        public String getRealmMapper() {
                            return finalRealmMapper;
                        }
                    });
                    factory.addMechanism(mec);
                }
            }
        }
        return factory;
    }

    public static ModelNode addSecurityDomain(CommandContext ctx, Realm realm,
            String newSecurityDomain) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SECURITY_DOMAIN, newSecurityDomain);
        ModelNode mn = buildSecurityDomainResource(realm);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    public static ModelNode addAuthFactory(CommandContext ctx, SecurityDomain securityDomain,
            String newAuthFactoryName, AuthFactorySpec spec) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(spec.getResourceType(), newAuthFactoryName);
        ModelNode mn = buildAuthFactoryResource(securityDomain, spec);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    public static void addAuthMechanism(CommandContext ctx, AuthFactory authFactory,
            AuthMechanism mechanism, ModelNode steps) throws OperationFormatException {
        ModelNode mechanisms = retrieveMechanisms(ctx, authFactory);
        ModelNode newMechanism = buildMechanismResource(mechanism);
        // check if a mechanism with the same name exists, replace it.
        int index = 0;
        boolean found = false;
        for (ModelNode m : mechanisms.asList()) {
            if (m.hasDefined(Util.MECHANISM_NAME)) {
                String name = m.get(Util.MECHANISM_NAME).asString();
                if (name.equals(mechanism.getType())) {
                    // Already have the exact same mechanism, no need to add it.
                    if (newMechanism.equals(m)) {
                        return;
                    }
                    found = true;
                    break;
                }
            }
            index += 1;
        }

        if (found) {
            mechanisms.remove(index);
            mechanisms.insert(newMechanism, index);
        } else {
            mechanisms.add(newMechanism);
        }

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(authFactory.getSpec().getResourceType(), authFactory.getName());
        builder.getModelNode().get(Util.VALUE).set(mechanisms);
        builder.getModelNode().get(Util.NAME).set(Util.MECHANISM_CONFIGURATIONS);
        steps.add(builder.buildRequest());
    }

    private static ModelNode retrieveMechanisms(CommandContext ctx, AuthFactory authFactory) {
        ModelNode mn = getAuthFactoryResource(authFactory.getName(), authFactory.getSpec(), ctx);
        if (mn == null) {
            return new ModelNode().setEmptyList();
        } else if (mn.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            return mn.get(Util.MECHANISM_CONFIGURATIONS);
        } else {
            return new ModelNode().setEmptyList();
        }
    }

    private static ModelNode retrieveSecurityDomainRealms(CommandContext ctx, SecurityDomain domain) {
        ModelNode mn = getSecurityDomainResource(domain, ctx);
        if (mn == null) {
            return new ModelNode().setEmptyList();
        } else if (mn.hasDefined(Util.REALMS)) {
            return mn.get(Util.REALMS);
        } else {
            return new ModelNode().setEmptyList();
        }
    }

    public static void addRealm(CommandContext ctx, SecurityDomain securityDomain, Realm realm, ModelNode steps) throws OperationFormatException {
        ModelNode realms = retrieveSecurityDomainRealms(ctx, securityDomain);
        ModelNode newRealm = buildRealmResource(realm);
        int index = 0;
        boolean found = false;
        for (ModelNode r : realms.asList()) {
            if (r.hasDefined(Util.REALM)) {
                String n = r.get(Util.REALM).asString();
                // Already present, skip....
                if (n.equals(realm.getName())) {
                    if (newRealm.equals(r)) {
                        return;
                    }
                    // We need to replace it with the new one.
                    found = true;
                    break;
                }
            }
            index += 1;
        }
        if (found) {
            realms.remove(index);
            realms.insert(newRealm, index);
        } else {
            realms.add(newRealm);
        }
        {
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.WRITE_ATTRIBUTE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(Util.SECURITY_DOMAIN, securityDomain.getName());
            builder.getModelNode().get(Util.VALUE).set(realms);
            builder.getModelNode().get(Util.NAME).set(Util.REALMS);
            steps.add(builder.buildRequest());
        }

        // Redefine the default-realm to be the new added realm
        // only if it has no realm-mapper.
        if (realm.getConfig().getRealmMapper() == null) {
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.WRITE_ATTRIBUTE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(Util.SECURITY_DOMAIN, securityDomain.getName());
            builder.getModelNode().get(Util.VALUE).set(realm.getName());
            builder.getModelNode().get(Util.NAME).set(Util.DEFAULT_REALM);
            steps.add(builder.buildRequest());
        }
    }

    public static boolean localUserExists(CommandContext ctx) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.IDENTITY_REALM, Util.LOCAL);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static String findKeyStoreRealm(CommandContext ctx, String trustStore) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        String name = null;
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, Util.KEY_STORE_REALM);
        ModelNode response = ctx.getModelControllerClient().execute(builder.buildRequest());
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode mn = response.get(Util.RESULT);
                for (String key : mn.keys()) {
                    ModelNode ksr = mn.get(key);
                    if (ksr.hasDefined(Util.KEY_STORE)) {
                        String ks = ksr.get(Util.KEY_STORE).asString();
                        if (ks.equals(trustStore)) {
                            name = key;
                            break;
                        }
                    }
                }
            }
        }
        return name;
    }

    public static ModelNode addKeyStoreRealm(CommandContext ctx, String ksRealmName, String keyStore) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE_REALM, ksRealmName);
        builder.addProperty(Util.KEY_STORE, keyStore);
        return builder.buildRequest();
    }

    public static ModelNode removeMechanisms(CommandContext ctx, ModelNode factory,
            String factoryName, AuthFactorySpec spec, Set<String> toRemove) throws Exception {
        if (factory.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            List<ModelNode> remains = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            ModelNode mechanisms = factory.get(Util.MECHANISM_CONFIGURATIONS);
            for (ModelNode m : mechanisms.asList()) {
                String name = m.get(Util.MECHANISM_NAME).asString();
                if (!toRemove.contains(name)) {
                    remains.add(m);
                }
                seen.add(name);
            }
            for (String r : toRemove) {
                if (!seen.contains(r)) {
                    throw new Exception("Mechanism " + r
                            + " is not contained in factory " + factoryName);
                }
            }
            if (remains.isEmpty()) {
                throw new Exception("Error: All mechanisms would be removed, this would fully disable access.");
            }
            ModelNode newValue = new ModelNode();
            newValue.set(remains);
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.WRITE_ATTRIBUTE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(spec.getResourceType(), factoryName);
            builder.addProperty(Util.NAME, Util.MECHANISM_CONFIGURATIONS);
            builder.getModelNode().get(Util.VALUE).set(newValue);
            return builder.buildRequest();
        } else {
            throw new Exception("No mechanism to remove in Factory " + factoryName);
        }
    }

    public static ModelNode reorderSASLFactory(CommandContext ctx, List<String> order, String factoryName) throws Exception {
        ModelNode factory = getAuthFactoryResource(factoryName, AuthFactorySpec.SASL, ctx);
        if (factory == null) {
            throw new Exception("Invalid factory name " + factoryName);
        }
        if (factory.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            ModelNode mechanisms = factory.get(Util.MECHANISM_CONFIGURATIONS);
            Set<String> seen = new HashSet<>();
            List<ModelNode> newOrder = new ArrayList<>();
            for (String o : order) {
                for (ModelNode m : mechanisms.asList()) {
                    String name = m.get(Util.MECHANISM_NAME).asString();
                    if (o.equals(name)) {
                        newOrder.add(m);
                    }
                    seen.add(name);
                }
            }
            for (String r : order) {
                if (!seen.contains(r)) {
                    throw new Exception("Mechanism " + r
                            + " is not contained in SASL factory " + factoryName);
                }
            }
            if (!order.containsAll(seen)) {
                throw new Exception("Mechanism list is not complete, existing mechanisms are:" + seen);
            }

            if (newOrder.isEmpty()) {
                throw new Exception("Error: All mechanisms would be removed, this would fully disable access.");
            }
            ModelNode newValue = new ModelNode();
            newValue.set(newOrder);
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.WRITE_ATTRIBUTE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(AuthFactorySpec.SASL.getResourceType(), factoryName);
            builder.addProperty(Util.NAME, Util.MECHANISM_CONFIGURATIONS);
            builder.getModelNode().get(Util.VALUE).set(newValue);
            return builder.buildRequest();
        } else {
            throw new Exception("No mechanism to re-order in Factory " + factoryName);
        }
    }

    public static List<String> getMechanisms(CommandContext ctx, String factoryName, AuthFactorySpec spec) throws Exception {
        ModelNode factory = getAuthFactoryResource(factoryName, spec, ctx);
        if (factory == null) {
            throw new Exception("Invalid factory name " + factoryName);
        }
        List<String> lst = new ArrayList<>();
        if (factory.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            ModelNode mechanisms = factory.get(Util.MECHANISM_CONFIGURATIONS);
            for (ModelNode m : mechanisms.asList()) {
                String name = m.get(Util.MECHANISM_NAME).asString();
                lst.add(name);
            }
        } else {
            throw new Exception("No mechanism in Factory " + factoryName);
        }
        return lst;
    }

    private static ModelNode getChildResource(String name, String type, CommandContext ctx) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(type, name);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                return outcome.get(Util.RESULT);
            }
        } catch (Exception e) {
        }
        return null;
    }


    public static ServerSSLContext getServerSSLContext(CommandContext context, String sslContextName) {
        ModelNode sslContext = getChildResource(sslContextName, Util.SERVER_SSL_CONTEXT, context);
        ServerSSLContext ctx = null;
        if (sslContext != null) {
            String kmName = sslContext.get(Util.KEY_MANAGER).asString();
            ModelNode km = getChildResource(kmName, Util.KEY_MANAGER, context);
            String ksName = km.get(Util.KEY_STORE).asString();
            KeyStore keyStore = new KeyStore(ksName, null, true);
            KeyManager keyManager = new KeyManager(kmName, keyStore, true);
            ctx = new ServerSSLContext(ksName, keyManager, true);
        }
        return ctx;
    }
}
