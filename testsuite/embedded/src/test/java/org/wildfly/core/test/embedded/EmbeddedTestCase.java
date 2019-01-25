/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.test.embedded;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.embedded.EmbeddedManagedProcess;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.HostController;
import org.wildfly.core.embedded.StandaloneServer;

import javax.swing.*;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class EmbeddedTestCase extends AbstractTestCase {

    @Test
    public void testStartAndStopStandalone() throws Exception {
        final StandaloneServer server = EmbeddedProcessFactory.createStandaloneServer(Environment.createConfigBuilder().build());

        try {
            server.start();
            testRunning(server, STANDALONE_CHECK);
        } finally {
            server.stop();
        }

        try {
            server.start();
            testRunning(server, STANDALONE_CHECK);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testStartAndStopHostController() throws Exception {
        final HostController server = EmbeddedProcessFactory.createHostController(Environment.createConfigBuilder().build());

        try {
            server.start();
            testRunning(server, HOST_CONTROLLER_CHECK);
        } finally {
            server.stop();
        }

        try {
            server.start();
            testRunning(server, HOST_CONTROLLER_CHECK);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testprototypeWFCORE_1187() throws Exception {
        // Prepare environment
        final Path jbossHome = Environment.JBOSS_HOME.toAbsolutePath();
        Assert.assertTrue("Could not create temp directory " + jbossHome.toString(), Files.exists(jbossHome));
        final Path firstJbossHome = Files.createTempDirectory("firstJbossHome");
        Assert.assertTrue("Could not create temp directory " + firstJbossHome.toString(), Files.exists(firstJbossHome));
        // In new jboss home directory is required wildfly-core structures and files
        /*
        [disconnected /] embed-server --jboss-home=/tmp/2019-01-25/WFCORE-1187/firstLog
        Cannot start embedded server: The first directory of the specified module path /tmp/2019-01-25/WFCORE-1187/firstLog
        /modules is invalid or does not exist.

        [disconnected /] embed-server --jboss-home=/tmp/2019-01-25/WFCORE-1187/firstLog
        Cannot start embedded server: WFLYEMB0014: Cannot load module org.jboss.vfs from: local module loader @5c606deb
        (finder: local module finder @511a025c (roots: /tmp/2019-01-25/WFCORE-1187/firstLog/modules)): org.jboss.vfs

        [disconnected /] embed-server --jboss-home=/tmp/2019-01-25/WFCORE-1187/firstLog
        Cannot start embedded server: WFLYEMB0022: Cannot invoke 'start' on embedded process: WFLYSRV0122:
        Server base directory does not exist: /tmp/2019-01-25/WFCORE-1187/firstLog/standalone
        */
        copyFileOrFolder(jbossHome.toFile(), firstJbossHome.toFile(), StandardCopyOption.REPLACE_EXISTING);
        final Path secondJbossHome = Files.createTempDirectory("secondJbossHome");
        Assert.assertTrue("Could not create temp directory " + secondJbossHome.toString(), Files.exists(secondJbossHome));
        copyFileOrFolder(jbossHome.toFile(), secondJbossHome.toFile(), StandardCopyOption.REPLACE_EXISTING);


        final StandaloneServer server = EmbeddedProcessFactory.createStandaloneServer(Environment.createConfigBuilder().build());

        try {
            server.start();
            waitFor(server, null);
        } finally {
            server.stop();
        }

        try {
            server.start();
            waitFor(server, null);
        } finally {
            server.stop();
        }

    }

    public static void copyFileOrFolder(File source, File dest, CopyOption... options) throws IOException {
        if (source.isDirectory())
            copyFolder(source, dest, options);
        else {
            ensureParentFolder(dest);
            copyFile(source, dest, options);
        }
    }

    private static void copyFolder(File source, File dest, CopyOption... options) throws IOException {
        if (!dest.exists())
            dest.mkdirs();
        File[] contents = source.listFiles();
        if (contents != null) {
            for (File f : contents) {
                File newFile = new File(dest.getAbsolutePath() + File.separator + f.getName());
                if (f.isDirectory())
                    copyFolder(f, newFile, options);
                else
                    copyFile(f, newFile, options);
            }
        }
    }

    private static void copyFile(File source, File dest, CopyOption... options) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), options);
    }

    private static void ensureParentFolder(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
    }

    private void testRunning(final EmbeddedManagedProcess server, final Function<EmbeddedManagedProcess, Boolean> check)
            throws IOException, TimeoutException, InterruptedException {
        waitFor(server, check);
        // Ensure the server has started
        try (ModelControllerClient client = server.getModelControllerClient()) {
            final ModelNode op = Operations.createReadAttributeOperation(AbstractTestCase.EMPTY_ADDRESS, "launch-type");
            final ModelNode result = executeOperation(client, op);
            Assert.assertEquals("EMBEDDED", result.asString());
        }
    }
}
