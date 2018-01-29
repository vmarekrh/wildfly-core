/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.test.integration.domain.management.cli;

import java.io.File;
import java.util.Iterator;
import org.jboss.as.cli.CommandContext;

import org.jboss.as.test.deployment.DeploymentInfoUtils;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;
import org.junit.After;
import org.junit.AfterClass;

import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class DeployAllDomainTestCase extends AbstractCliTestBase {

    protected static File cliTestApp1War;
    protected static File cliTestApp2War;
    protected static File cliTestAnotherWar;
    protected static File cliTestAppEar;

    protected static String sgOne;
    protected static String sgTwo;

    protected static CommandContext ctx;
    protected static DomainTestSupport testSupport;
    protected static DeploymentInfoUtils infoUtils;

    @BeforeClass
    public static void before() throws Exception {
        testSupport = CLITestSuite.createSupport(UndeployWildcardDomainTestCase.class.getSimpleName());
        infoUtils = new DeploymentInfoUtils(DomainTestSupport.masterAddress);

        String tempDir = System.getProperty("java.io.tmpdir");

        // deployment1
        WebArchive war = ShrinkWrap.create(WebArchive.class, "cli-test-app1-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version0"), "page.html");
        cliTestApp1War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);

        // deployment2
        war = ShrinkWrap.create(WebArchive.class, "cli-test-app2-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version1"), "page.html");
        cliTestApp2War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp2War, true);

        // deployment3
        war = ShrinkWrap.create(WebArchive.class, "cli-test-another-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version2"), "page.html");
        cliTestAnotherWar = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestAnotherWar, true);

        // deployment4
        war = ShrinkWrap.create(WebArchive.class, "cli-test-app3-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version3"), "page.html");
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class,
                "cli-test-app-deploy-all.ear");
        ear.add(war, new BasicPath("/"), ZipExporter.class);
        cliTestAppEar = new File(tempDir + File.separator + ear.getName());
        new ZipExporterImpl(ear).exportTo(cliTestAppEar, true);

        final Iterator<String> sgI = CLITestSuite.serverGroups.keySet().iterator();
        if (!sgI.hasNext()) {
            fail("Server groups aren't available.");
        }
        sgOne = sgI.next();
        if (!sgI.hasNext()) {
            fail("Second server groups isn't available.");
        }
        sgTwo = sgI.next();
    }

    @AfterClass
    public static void after() throws Exception {

        CLITestSuite.stopSupport();

        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
        cliTestAppEar.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();
    }

    @After
    public void afterTest() throws Exception {
        ctx.terminateSession();
    }

    @Test
    public void testDeploymentLiveCycleWithServerGroups() throws Exception {
        // TODO re-make test
        /*
        *For check are used commands 'deployment list' and 'deployment info'
        *Deploy 3 applications deployments with set server groups
        *Check if deployment is installed and sets server groups
        *Disable 2 applications deployments with set server groups
        *Check if applications deployments is disabled
        Disable all applications deployments by all server groups
        Check if all applications deployments is disabled
        Enable one application deployment
        Check if selected application deployment is enabled
        Undeploy one application deployment
        Check if selected application deployment is removed
         */

        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName());
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName());
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName(), ENABLED);
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ENABLED);

        // Step 3a) Disabling two selected applications deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());


        // Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail
        try {
            ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp2War.getName());
            fail("Disabling application deployment with wrong server group doesn't failed! Command execution fail is expected.");
        } catch (Exception ex){
            // Verification wrong command execution fail - success
        }

        // Step 4) Verify if two selected applications deployments are disabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName(), ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ENABLED);

        // Step 5) Disable all deployed applications deployments in all server groups
        // TODO #BB01 after fix remove disable with specific group ant get global server group
//        ctx.handle("deployment disable-all --all-relevant-server-groups");
        // TODO remove after test debug finish
//        ctx.handle("deployment disable-all --server-groups=" + sgOne);
//        ctx.handle("deployment disable-all --server-groups=" + sgTwo);
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());
        // #BB01

        // Step 5) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestApp2War.getName(), ADDED);
        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ADDED);

//        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
//        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
//        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
//        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());
//
//        // Disable them all.
//        ctx.handle("deployment disable-all --all-relevant-server-groups");
//
//        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestApp1War.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ADDED);
//
//        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp2War.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ADDED);
//        // Deploy them all.
//        ctx.handle("deploy --name=* --server-groups=" + sgTwo + ',' + sgOne);
//        checkDeployment(sgOne, cliTestApp1War.getName(), true);
//        checkDeployment(sgOne, cliTestAnotherWar.getName(), true);
//        checkDeployment(sgOne, cliTestAppEar.getName(), true);
//
//        checkDeployment(sgTwo, cliTestApp2War.getName(), true);
//        checkDeployment(sgTwo, cliTestAppEar.getName(), true);
//
//        ctx.handle("deployment disable-all --all-relevant-server-groups");
//
//        checkDeployment(sgOne, cliTestApp1War.getName(), false);
//        checkDeployment(sgOne, cliTestAnotherWar.getName(), false);
//        checkDeployment(sgOne, cliTestAppEar.getName(), false);
//
//        checkDeployment(sgTwo, cliTestApp2War.getName(), false);
//        checkDeployment(sgTwo, cliTestAppEar.getName(), false);
//        // Deploy them all.
//        ctx.handle("deployment enable-all --server-groups=" + sgTwo + ',' + sgOne);
//        checkDeployment(sgOne, cliTestApp1War.getName(), true);
//        checkDeployment(sgOne, cliTestAnotherWar.getName(), true);
//        checkDeployment(sgOne, cliTestAppEar.getName(), true);
//
//        checkDeployment(sgTwo, cliTestApp2War.getName(), true);
//        checkDeployment(sgTwo, cliTestAppEar.getName(), true);
//
//        ctx.handle("deployment undeploy * --all-relevant-server-groups");
    }

    @Test
    public void testDeploymentLegacyLiveCycleWithServerGroups() throws Exception {
        // TODO re-make test
        /*
        For check are used commands 'deployment list' and 'deployment info'
        Deploy 3 applications deployments with set server groups
        Check if deployment is installed and sets server groups
        Disable 2 applications deployments with set server groups
        Check if applications deployments is disabled
        Enable one application deployment
        Check if selected application deployment is enabled
        Undeploy one application deployment
        Check if selected application deployment is removed
         */

//        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
//        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
//        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
//        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());
//
//        // Disable them all.
//        ctx.handle("undeploy * --keep-content --all-relevant-server-groups");
//
//        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestApp1War.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestAnotherWar.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ADDED);
//
//        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp2War.getName(), ADDED);
//        infoUtils.reCheckMemorisedOutput(cliTestAppEar.getName(), ADDED);
//        // Deploy them all.
//        ctx.handle("deploy --name=* --server-groups=" + sgTwo + ',' + sgOne);
//        checkDeployment(sgOne, cliTestApp1War.getName(), true);
//        checkDeployment(sgOne, cliTestAnotherWar.getName(), true);
//        checkDeployment(sgOne, cliTestAppEar.getName(), true);
//
//        checkDeployment(sgTwo, cliTestApp2War.getName(), true);
//        checkDeployment(sgTwo, cliTestAppEar.getName(), true);
//
//        ctx.handle("deployment disable-all --all-relevant-server-groups");
//
//        checkDeployment(sgOne, cliTestApp1War.getName(), false);
//        checkDeployment(sgOne, cliTestAnotherWar.getName(), false);
//        checkDeployment(sgOne, cliTestAppEar.getName(), false);
//
//        checkDeployment(sgTwo, cliTestApp2War.getName(), false);
//        checkDeployment(sgTwo, cliTestAppEar.getName(), false);
//        // Deploy them all.
//        ctx.handle("deployment enable-all --server-groups=" + sgTwo + ',' + sgOne);
//        checkDeployment(sgOne, cliTestApp1War.getName(), true);
//        checkDeployment(sgOne, cliTestAnotherWar.getName(), true);
//        checkDeployment(sgOne, cliTestAppEar.getName(), true);
//
//        checkDeployment(sgTwo, cliTestApp2War.getName(), true);
//        checkDeployment(sgTwo, cliTestAppEar.getName(), true);
//
//        ctx.handle("undeploy * --all-relevant-server-groups");
    }

//    private void checkDeployment(String serverGroup, String name, boolean enabled) throws CommandFormatException, IOException {
//        ModelNode mn = ctx.buildRequest("/server-group=" + serverGroup
//                + "/deployment=" + name + ":read-attribute(name=enabled)");
//        ModelNode response = ctx.getModelControllerClient().execute(mn);
//        if (response.hasDefined(Util.OUTCOME) && response.get(Util.OUTCOME).asString().equals(Util.SUCCESS)) {
//            if (!response.hasDefined(RESULT)) {
//                throw new CommandFormatException("No result for " + name);
//            }
//            if (!response.get(RESULT).asBoolean() == enabled) {
//                throw new CommandFormatException(name + " not in right state");
//            }
//        } else {
//            throw new CommandFormatException("Invalid response for " + name);
//        }
//    }
}
