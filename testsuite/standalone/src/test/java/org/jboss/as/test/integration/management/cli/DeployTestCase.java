/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;

import static org.jboss.as.cli.Util.RESULT;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.STOPPED;
import static org.junit.Assert.assertEquals;

import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.deployment.DeploymentInfoUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;


import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class DeployTestCase {

    private static File cliTestApp1War;
    private static File cliTestApp2War;
    private static File cliTestAnotherWar;

    private static DeploymentInfoUtils infoUtils;
    private static CommandContext ctx;

    @BeforeClass
    public static void before() throws Exception {
        // TODO replace Legacy command by aesh command
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setInitConsole(true).setConsoleInput(System.in).setConsoleOutput(System.out).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();
        infoUtils = new DeploymentInfoUtils(TestSuiteEnvironment.getServerAddress());

        String tempDir = System.getProperty("java.io.tmpdir");

        // deployment1
        WebArchive war = ShrinkWrap.create(WebArchive.class, "cli-test-app1-deploy.war");
        war.addAsWebResource(new StringAsset("Version0"), "page.html");
        cliTestApp1War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);

        // deployment2
        war = ShrinkWrap.create(WebArchive.class, "cli-test-app2-deploy.war");
        war.addAsWebResource(new StringAsset("Version1"), "page.html");
        cliTestApp2War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp2War, true);

        // deployment3
        war = ShrinkWrap.create(WebArchive.class, "cli-test-another-deploy.war");
        war.addAsWebResource(new StringAsset("Version2"), "page.html");
        cliTestAnotherWar = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestAnotherWar, true);

        ctx.handle("deployment deploy-file --disabled " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --disabled " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --disabled " + cliTestApp2War.getAbsolutePath());

//        ctx.handle("deploy --disabled " + cliTestApp1War.getAbsolutePath());
//        ctx.handle("deploy --disabled " + cliTestAnotherWar.getAbsolutePath());
//        ctx.handle("deploy --disabled " + cliTestApp2War.getAbsolutePath());

    }

    @AfterClass
    public static void after() throws Exception {
        ctx.handle("deployment undeploy *");
        ctx.terminateSession();
        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        infoUtils.readDeploymentInfo();
        if (infoUtils.getStateByOutputMemory(cliTestApp1War.getName()) != STOPPED) {
            ctx.handle("deployment disable " + cliTestApp1War.getName());
        }
        if (infoUtils.getStateByOutputMemory(cliTestAnotherWar.getName()) != STOPPED) {
            ctx.handle("deployment disable " + cliTestAnotherWar.getName());
        }
        if (infoUtils.getStateByOutputMemory(cliTestApp2War.getName()) != STOPPED) {
            ctx.handle("deployment disable " + cliTestApp2War.getName());
        }
        infoUtils.readDeploymentInfo();
        infoUtils.checkExistInOutputMemory(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), STOPPED);
    }

    @Test
    public void testDeploymentLiveCycle() throws Exception {
        // TODO re-make test
        /*
        For check are used commands 'deployment list' and 'deployment info'
        Deploy 3 applications deployments
        Check if deployment is installed
        Disable one applications deployments
        Check if applications deployments is disabled
        Disable all applications deployments
        Check if applications deployments is disabled
        Enable one application deployment
        Check if selected application deployment is enabled
        Enable all applications deployments
        Check if all applications deployments is enabled
        Undeploy one application deployment
        Check if selected application deployment is removed
        Undeploy all remaining application deployment
        Check if all applications deployments are gone
         */
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestApp2War.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);

        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ENABLED);

        // Step 3a) Disabling two selected applications deployments
        ctx.handle("deployment disable " + cliTestApp1War.getName());
        ctx.handle("deployment disable " + cliTestApp2War.getName());


        // Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail
        try {
            ctx.handle("deployment disable " + cliTestApp2War.getName());
            fail("Disabling application deployment with wrong server group doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Verification wrong command execution fail - success
        }

        // Step 4) Verify if two selected applications deployments are disabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);

        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);

        // Step 5) Disable all deployed applications deployments in all server groups
        // TODO #BB01 after fix remove disable with specific group ant get global server group
//        ctx.handle("deployment disable-all --all-relevant-server-groups");
        // TODO remove after test debug finish
//        ctx.handle("deployment disable-all --server-groups=" + sgOne);
//        ctx.handle("deployment disable-all --server-groups=" + sgTwo);
        ctx.handle("deployment disable " + cliTestApp1War.getName());
        ctx.handle("deployment disable " + cliTestAnotherWar.getName());
        ctx.handle("deployment disable " + cliTestApp2War.getName());
        // #BB01

        // Step 6) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);

        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);

        // TODO dal ověřit zda li je to podle pravdy - psáno pouze
        // Step 7) Enable one application deployment
//        ctx.handle("deployment enable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 8) Verify if selected application deployment are enabled, but other have still previous state
//        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
//
//        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);

        // Step 9) Enable all applications deployments for all server groups
        ctx.handle("deployment enable-all --all-server-groups");

        // Step 10) Verify if all applications deployments are enabled for all server groups
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);

        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ENABLED);

        // Step 11) Undeploy one application deployment
        ctx.handle("deployment undeploy " + cliTestApp2War.getName());
        // Step 12) Check if selected application deployment is removed, but others still exist with right state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());

        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());

        // Step 13) Undeploy all applications deployments
        ctx.handle("deployment undeploy * --all-relevant-server-groups");
        // Step 14) Check if all applications deployments is gone
        infoUtils.readDeploymentList();
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
    }

    @Test
    public void testDeployAllCompletion() throws Exception {
        {
            String cmd = "deploy --name=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("*"));
            assertTrue(candidates.toString(), candidates.contains(cliTestApp1War.getName()));
            assertTrue(candidates.toString(), candidates.contains(cliTestAnotherWar.getName()));
            assertTrue(candidates.toString(), candidates.contains(cliTestApp2War.getName()));
        }

        {
            String cmd = "deploy --name=*";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("* "));
            assertTrue(candidates.toString(), candidates.size() == 1);
        }
    }

    @Test
    public void testLegacyRedeployFileDeployment() throws Exception {
        // TODO re-make test
        /*
        Prepare application deployment for redeploy

        Check if prepared application deployments is installed

        Try redeploy same application deployment

        Check if application deployments is redeployed

        Using backward compatibility commands
         */
        redeploy("deploy --force", false);
        redeploy("deploy --force", true);
    }

    private void redeploy(String cmd, boolean enabled) throws Exception {
//        WebArchive war = ShrinkWrap.create(WebArchive.class, "cli-test-app1-deploy.war");
//        war.addAsWebResource(new StringAsset("Version0.1"), "page.html");
//        cliTestApp1War.delete();
//        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);
//        {
//            ctx.handle(cmd + " " + cliTestApp1War.getAbsolutePath());
//            checkDeployment(cliTestApp1War.getName(), enabled);
//        }
//        String op;
//        if(enabled) {
//            op = "undeploy";
//        } else {
//            op = "deploy";
//        }
//        ctx.handle("/deployment=" + cliTestApp1War.getName() + ':'+op+"()");
//        assertEquals(!enabled, readDeploymentStatus(cliTestApp1War.getName()));
//        war = ShrinkWrap.create(WebArchive.class, "cli-test-app1-deploy.war");
//        war.addAsWebResource(new StringAsset("Version0.2"), "page.html");
//        cliTestApp1War.delete();
//        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);
//        {
//            ctx.handle(cmd + " " + cliTestApp1War.getAbsolutePath());
//            checkDeployment(cliTestApp1War.getName(), !enabled);
//        }
    }

    @Test
    public void testRedeployFileDeployment() throws Exception {
        // TODO re-make test
        /*
        For check are used commands 'deployment list'

        Prepare application deployment for redeploy

        Check if prepared application deployments is installed

        Try redeploy same application deployment

        Check if application deployments is redeployed
         */
        redeploy("deployment deploy-file --replace", false);
        redeploy("deployment deploy-file --replace", true);
    }

    @Test
    public void testLegacyDeployUndeployViaCliArchive() throws Exception {
        // TODO re-make test
        /*
        Deploy one application deployment via cli archive

        Using backward compatibility commands
         */
        File cliFile = createCliArchive();
        try {
            ctx.handle("deploy " + cliFile.getAbsolutePath());
        } finally {
            cliFile.delete();
        }
    }

    @Test
    public void testDeployUndeployViaCliArchive() throws Exception {
        // TODO re-make test
        /*
        Deploy one application deployment via cli archive
         */
        File cliFile = createCliArchive();
        try {
            ctx.handle("deployment deploy-cli-archive " + cliFile.getAbsolutePath());
        } finally {
            cliFile.delete();
        }
    }

    @Test
    public void testLegacyDeployUndeployViaCliArchiveWithTimeout() throws Exception {
        // TODO re-make test
        /*
        Operation is limited by 2000 second only

        Deploy one application deployment via cli archive

        Using backward compatibility commands
         */
        File cliFile = createCliArchive();
        try {
            ctx.handle("command-timeout set 2000");
            ctx.handle("deploy " + cliFile.getAbsolutePath());
        } finally {
            cliFile.delete();
        }
    }

    @Test
    public void testDeployUndeployViaCliArchiveWithTimeout() throws Exception {
        // TODO re-make test
        /*
        Operation is limited by 2000 second only

        Deploy one application deployment via cli archive
         */
        File cliFile = createCliArchive();
        try {
            ctx.handle("command-timeout set 2000");
            ctx.handle("deployment deploy-cli-archive " + cliFile.getAbsolutePath());
        } finally {
            cliFile.delete();
        }
    }

    @Test
    public void testLegacyDisableEnableDeployments() throws Exception {
        // TODO make test
        /*
        Deploy disabled 3 applications deployments

        Check if applications deployments is installed and disabled

        Enable all applications deployments

        Check if applications deployments is enabled

        Disable all applications deployments

        Check if applications deployments is disabled

        Using backward compatibility commands
         */
    }

    @Test
    public void testDeployViaUrl() throws Exception {
        // TODO make test
        /*
        For check are used commands 'deployment list'

        Deploy application deployment via url link

        Check if application deployments is installed
         */
    }

    @Test
    public void testDeploymentInformation() throws Exception {
        // TODO make test
        /*
        For check are used commands 'deployment list' and 'deployment info'

        Deploy 3 applications deployments

        Disable one deployed deployment

        Parse and check deployment info command output
         */
    }

    @Test
    public void testDeployFileWithWrongPath() throws Exception {
        // TODO make test
        /*
        Try deploy application deployments with wrong path

        Check error message
         */
    }

    @Test
    public void testDeployWithWrongUrl() throws Exception {
        // TODO make test
        /*
        Try deploy application deployments with wrong url

        Check error message
         */
    }

    @Test
    public void testDeployWithWrongCli() throws Exception {
        // TODO make test
        /*
        Try deploy application deployments with wrong path

        Check error message
         */
    }

    @Test
    public void testDisableWrongDeployment() throws Exception {
        // TODO make test
        /*
        Try disable non installed application deployment

        Check error message
         */

    }

    @Test
    public void testDisableAlreadyDisabledDeployment() throws Exception {
        // TODO make test
        /*
        Deploy disabled application deployment

        Check if application deployment is installed and disabled

        Try disable already disabled application deployment

        Check error message
         */
    }

    @Test
    public void testEnableWrongDeployments() throws Exception {
        // TODO make test
        /*
        Try enable non installed application deployment

        Check error message
         */
    }

//    private void checkDeployment(String name, boolean enabled) throws CommandFormatException, IOException {
//        if (readDeploymentStatus(name) != enabled) {
//            throw new CommandFormatException(name + " not in right state");
//        }
//    }
//
//    private boolean readDeploymentStatus(String name) throws CommandFormatException, IOException {
//        ModelNode mn = ctx.buildRequest("/deployment=" + name + ":read-attribute(name=enabled)");
//        ModelNode response = ctx.getModelControllerClient().execute(mn);
//        if (response.hasDefined(Util.OUTCOME) && response.get(Util.OUTCOME).asString().equals(Util.SUCCESS)) {
//            if (!response.hasDefined(RESULT)) {
//                throw new CommandFormatException("No result for " + name);
//            }
//            return response.get(RESULT).asBoolean() ;
//        }
//        throw new CommandFormatException("No result for " + name);
//    }

    private static File createCliArchive() {

        final GenericArchive cliArchive = ShrinkWrap.create(GenericArchive.class, "deploymentarchive.cli");
        cliArchive.add(new StringAsset("ls -l"), "deploy.scr");

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        final File file = new File(tempDir, "deploymentarchive.cli");
        cliArchive.as(ZipExporter.class).exportTo(file, true);
        return file;
    }
}
