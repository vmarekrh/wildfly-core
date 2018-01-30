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
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createCliArchive;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createWarArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.STOPPED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.OK;
import static org.junit.Assert.assertEquals;

import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.deployment.DeploymentArchiveUtils;
import org.jboss.as.test.deployment.DeploymentInfoUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.After;
import org.junit.AfterClass;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;


import org.junit.BeforeClass;
import org.junit.Ignore;
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

        // deployment1
        cliTestApp1War = createWarArchive("cli-test-app1-deploy.war", "Version0");

        // deployment2
        cliTestApp2War = createWarArchive("cli-test-app2-deploy.war", "Version1");

        // deployment3
        cliTestAnotherWar = createWarArchive("cli-test-another-deploy.war", "Version2");

//        ctx.handle("deployment deploy-file --disabled " + cliTestApp1War.getAbsolutePath());
//        ctx.handle("deployment deploy-file --disabled " + cliTestAnotherWar.getAbsolutePath());
//        ctx.handle("deployment deploy-file --disabled " + cliTestApp2War.getAbsolutePath());

//        ctx.handle("deploy --disabled " + cliTestApp1War.getAbsolutePath());
//        ctx.handle("deploy --disabled " + cliTestAnotherWar.getAbsolutePath());
//        ctx.handle("deploy --disabled " + cliTestApp2War.getAbsolutePath());
    }

    @AfterClass
    public static void after() throws Exception {
        ctx.terminateSession();
        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
    }

    @Before
    public void beforeTest() throws Exception {
//        infoUtils.readDeploymentInfo();
//        if (infoUtils.getStateByOutputMemory(cliTestApp1War.getName()) != STOPPED) {
//            ctx.handle("deployment disable " + cliTestApp1War.getName());
//        }
//        if (infoUtils.getStateByOutputMemory(cliTestAnotherWar.getName()) != STOPPED) {
//            ctx.handle("deployment disable " + cliTestAnotherWar.getName());
//        }
//        if (infoUtils.getStateByOutputMemory(cliTestApp2War.getName()) != STOPPED) {
//            ctx.handle("deployment disable " + cliTestApp2War.getName());
//        }
//        infoUtils.readDeploymentInfo();
//        infoUtils.checkExistInOutputMemory(cliTestApp1War.getName(), STOPPED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), STOPPED);
    }

    @After
    public void afterTest() throws Exception {
        if (infoUtils.readDeploymentList() != null)
            ctx.handle("deployment undeploy *");
    }


    @Test
    public void testDeploymentLiveCycle() throws Exception {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestApp2War.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 3a) Disabling selected application deployment
        ctx.handle("deployment disable " + cliTestApp1War.getName());

        // Step 4) Verify if selected application deployment is disabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 5) Disable all deployed applications deployments in all server groups
        ctx.handle("deployment disable-all");

        // Step 6) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), STOPPED);

        // Step 7) Enable selected application deployment
        ctx.handle("deployment enable " + cliTestApp2War.getName());

        // Step 8) Verify if selected application deployment are enabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 9) Enable all applications deployments for all server groups
        ctx.handle("deployment enable-all");

        // Step 10) Verify if all applications deployments are enabled for all server groups
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 11) Undeploy one application deployment
        ctx.handle("deployment undeploy " + cliTestApp2War.getName());
        // Step 12) Check if selected application deployment is removed, but others still exist with right state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());

        // Step 13) Undeploy all applications deployments
        ctx.handle("deployment undeploy *");
        // Step 14) Check if all applications deployments is gone
        infoUtils.readDeploymentList();
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
    }

    @Ignore
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

    @Ignore
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

        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    @Ignore
    @Test
    public void testDeployViaUrl() throws Exception {
        // TODO make test
        /*
        For check are used commands 'deployment list'

        Deploy application deployment via url link

        Check if application deployments is installed
         */
    }

    @Ignore
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

    @Ignore
    @Test
    public void testDeployFileWithWrongPath() throws Exception {
        // TODO make test
        /*
        Try deploy application deployments with wrong path

        Check error message
         */
    }

    @Ignore
    @Test
    public void testDeployWithWrongUrl() throws Exception {
        // TODO make test
        /*
        Try deploy application deployments with wrong url

        Check error message
         */
    }

    @Ignore
    @Test
    public void testDeployWithWrongCli() throws Exception {
        // TODO make test
        /*
        Try deploy application deployments with wrong path

        Check error message
         */
    }

    @Ignore
    @Test
    public void testDisableWrongDeployment() throws Exception {
        // TODO make test
        /*
        Try disable non installed application deployment

        Check error message
         */

    }

    @Ignore
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

    @Ignore
    @Test
    public void testEnableWrongDeployments() throws Exception {
        // TODO make test
        /*
        Try enable non installed application deployment

        Check error message
         */
    }
}
