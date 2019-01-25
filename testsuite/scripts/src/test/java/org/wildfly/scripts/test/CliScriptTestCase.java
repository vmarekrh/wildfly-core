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

package org.wildfly.scripts.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CliScriptTestCase extends ScriptTestCase {

    public CliScriptTestCase() {
        super("jboss-cli");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        // Read an attribute
        script.start(MAVEN_JAVA_OPTS, "--commands=embed-server,:read-attribute(name=server-state),exit");
        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        validateProcess(script);

        // Read the output lines which should be valid DMR
        try (InputStream in = Files.newInputStream(script.getStdout())) {
            final ModelNode result = ModelNode.fromStream(in);
            if (!Operations.isSuccessfulOutcome(result)) {
                Assert.fail(result.asString());
            }
            Assert.assertEquals(ClientConstants.CONTROLLER_PROCESS_STATE_RUNNING, Operations.readResult(result).asString());
        }
    }

    /**
     * Testing jboss-cli script to not throw divide by zero exception while are running as a cron service without tty.
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-4278">WFCORE-4278</a>
     */
    @Test
    public void testDivideByZeroWithoutTty() throws Exception {
//        try {
//            Assume.assumeTrue(!Environment.isWindows() && isShellSupported("bash", "-c", "echo", "test"));
//        } catch (Exception ex) {
//            //
//        }
        final Path fullExe = Environment.JBOSS_HOME.resolve("bin").resolve("jboss-cli.sh").toAbsolutePath();
//        final ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", "echo AAAA");
        Process process = null;
        try (ScriptProcess cli = new ScriptProcess(fullExe, null)) {
            cli.start("connect");
            if (!cli.waitFor(Environment.getTimeout(), TimeUnit.SECONDS)) {
                Assert.fail("Timeout...");
            }
            System.out.println(cli.getErrorMessage("Tst"));
        }
//        try {
////            process = builder.start();
////            if (!process.waitFor(Environment.getTimeout(), TimeUnit.SECONDS)) {
////                Assert.assertTrue("Timeout...", false);
////            }
////            Assert.assertTrue(process.exitValue() == 0);
//        } catch (IOException e) {
//            Assert.assertTrue("Exception: " + e.getMessage(), false);
//        } finally {
//            if (process != null) {
//                process.destroyForcibly();
//            }
//        }
    }

}
