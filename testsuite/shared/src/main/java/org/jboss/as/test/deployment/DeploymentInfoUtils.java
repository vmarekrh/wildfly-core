/*
Copyright 2018 Red Hat, Inc.

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

package org.jboss.as.test.deployment;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.common.annotation.NotNull;

import java.io.IOException;

import static org.hamcrest.core.AllOf.allOf;
import static org.jboss.as.cli.Util.FAILED;
import static org.jboss.as.cli.Util.FAILURE_DESCRIPTION;
import static org.jboss.as.cli.Util.OUTCOME;
import static org.jboss.as.cli.Util.RESULT;
import static org.jboss.as.cli.Util.SUCCESS;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.UNKNOWN;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.mapBooleanByDeploymentStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.fail;

/**
 * Utils for verify state of applications deployments using command 'deployments list' and 'deployments info'.
 * Uses legacy and Aesh version of commands.
 * Direct verifying output of this commands.
 * Support double check verification in case set CommandContext by management command.
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 **/
public class DeploymentInfoUtils extends AbstractCliTestBase {
    private static final String OUTPUT_EMPTY_MARK = "--OUTPUT EMPTY--";

    private static final Logger log = Logger.getLogger(DeploymentInfoUtils.class);

    // Ip address for connection to cli by 'AbstractCliTestBase.initCLI(ipAddress)'
    private String ipAddress;
    // In case you disabled double checking, this variable is for enable double checking without parameter
    private CommandContext disabledCtx;
    private CommandContext ctx;

    // Holding output of last time called command for multiple checking output without recall command
    private String[] currentOutputRows;
    // Server group name in last time called command
    private String currentServerGroup;

    public DeploymentInfoUtils(String ipAddress) {
        // initialize CLI Wrapper, because for testing require raw command output
        this.ipAddress = ipAddress;
    }

    /**
     * Before you start checking, do not forget to connect to cli session
     *
     * @throws Exception
     */
    public void connectCli() throws Exception {
        AbstractCliTestBase.initCLI(ipAddress);
    }

    /**
     * After testing do not forget do close cli session
     *
     * @throws Exception
     */
    public void disconnectCli() throws Exception {
        AbstractCliTestBase.closeCLI();
        this.resetDoubleCheck();
    }

    /**
     * Enabling double checking
     * Use pre enabled {@link CommandContext} cli session
     */
    public void enableDoubleCheck() {
        enableDoubleCheck(this.disabledCtx);
    }

    /**
     * Enabling double checking
     * For use double checking state of application deployment by management command you must se CommandContext
     *
     * @param ctx {@link CommandContext} cli session
     */
    public void enableDoubleCheck(CommandContext ctx) {
        if (ctx == null) {
            throw new IllegalStateException("Could not accept null for enable double checking!");
        }
        if (ctx.isTerminated())
            throw new IllegalStateException("Could not accept closed cli for enable double checking!");
        this.resetDoubleCheck();
        this.ctx = ctx;
    }

    /**
     * Disabling double checking
     */
    public void disableDoubleCheck() {
        this.disabledCtx = this.ctx;
        this.ctx = null;
    }

    /**
     * Disable double checking and discard saved {@link CommandContext} cli session
     */
    public void resetDoubleCheck() {
        this.ctx = null;
        this.disabledCtx = null;
    }

    /**
     * Represent application deployment status.
     * Status of deployment you can read by command 'deployment info'.
     */
    public enum DeploymentState {
        // Statuses of Domain
        ENABLED("enabled"), // Represent installed in selected server group and enabled application deployment
        ADDED("added"), // Represent installed in selected server group but disabled application deployment
        NOT_ADDED("not added"), // Represents application deployment of other server group that selected server group

        // Statuses of Standalone
        OK("OK"), // Represent enabled application deployment
        STOPPED("STOPPED"), // Represent disabled application deployment

        // Error status
        UNKNOWN("!--unknown--!"); // Default value if isn't found

        private String title;

        DeploymentState(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        /**
         * Return information about enabled or disabled state from deployment info command statuses
         *
         * @param status Status of application deployment
         * @return true application deployment id enabled, false is disabled
         */
        public static boolean mapBooleanByDeploymentStatus(@NotNull DeploymentState status) {
            switch (status) {
                case OK:
                case ENABLED:
                    return true;
                case ADDED:
                case NOT_ADDED:
                case STOPPED:
                    return false;
                default:
                    throw new IllegalArgumentException("Unsupported state " + status + "!");
            }
        }
    }

    // #### BEGIN Direct checking methods
    public void checkDeploymentByList(String name) throws CommandFormatException, IOException {
        this.readDeploymentList();
        this.checkMemory(name);
    }

    /**
     * Call command to get information about application deployment and checking for his state and existence
     * For standalone mode.
     *
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkDeploymentByInfo(String name, DeploymentState expected) throws CommandFormatException, IOException {
        this.readDeploymentInfo();
        this.checkMemory(name, expected);
    }

    /**
     * Call command to get information about application deployment and checking for his state and existence
     * For domain mode.
     *
     * @param serverGroup Server group in case of domain mode run
     * @param name        Represent name of application deployment for testing
     * @param expected    Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkDeploymentByInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException, IOException {
        this.readDeploymentInfo(serverGroup);
        checkMemory(name, expected);
    }

    /**
     * Call command to get information about application deployment and checking for his state and existence
     * For standalone mode.
     * Using Legacy command.
     *
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkDeploymentByLegacyInfo(String name, DeploymentState expected) throws CommandFormatException, IOException {
        readLegacyDeploymentInfo();
        checkMemory(name, expected);
    }

    /**
     * Call command to get information about application deployment and checking for his state and existence
     * For domain mode.
     * Using Legacy command.
     *
     * @param serverGroup Server group in case of domain mode run
     * @param name        Represent name of application deployment for testing
     * @param expected    Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkDeploymentByLegacyInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException, IOException {
        readLegacyDeploymentInfo(serverGroup);
        checkMemory(name, expected);
    }
    // #### END   Direct checking methods

    // #### BEGIN Public pre-loading methods

    /**
     * Read information from command 'deployment List'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function without expected state
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public String readDeploymentList() {
        this.currentServerGroup = null;
        return callCommand("deployment list");
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expected state
     * For standalone mode.
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public String readDeploymentInfo() {
        return this.readDeploymentInfo(null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expected state
     * For domain mode.
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public String readDeploymentInfo(String serverGroup) {
        this.currentServerGroup = serverGroup;
        String groupPart = this.currentServerGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand("deployment info" + groupPart);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expected state
     * For standalone mode.
     * Using Legacy command.
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public String readLegacyDeploymentInfo() {
        return this.readLegacyDeploymentInfo(null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expected state
     * For domain mode.
     * Using Legacy command.
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public String readLegacyDeploymentInfo(String serverGroup) {
        this.currentServerGroup = serverGroup;
        String groupPart = this.currentServerGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand("deployment-info" + groupPart);
    }
    // #### END   Public pre-loading methods

    // #### BEGIN Method for checking without recalling command for applications deployments state

    /**
     * Checking for existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkExistInOutputMemory(String name) throws CommandFormatException, IOException {
        checkMemory(name);
    }

    /**
     * Checking for non existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkMissingInOutputMemory(String name) throws CommandFormatException, IOException {
        checkMemory(name, true);
    }

    /**
     * Checking for state and existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public void checkExistInOutputMemory(String name, DeploymentState expected) throws CommandFormatException, IOException {
        checkMemory(name, expected);
    }

    /**
     * Checking if called command has empty output.
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @return If command output is empty return true, else If command has some output return false
     * @throws CommandFormatException If you don't call info method! Nothing to check!
     */
    public boolean isOutputEmpty() throws CommandFormatException {
        if (this.currentOutputRows == null || this.currentOutputRows.length <= 0) {
            throw new CommandFormatException("Error: Nothing to check! Call first info command!");
        }
        return this.currentOutputRows.length <= 1 && this.currentOutputRows[0].contains(OUTPUT_EMPTY_MARK);
    }

    /**
     * In case you need know state of application deployment
     *
     * @param name Name of application deployment
     * @return State of application deployment, if not found return UNKNOWN state
     * @throws CommandFormatException
     */
    public DeploymentState getStateByOutputMemory(String name) throws CommandFormatException {
        this.isOutputEmpty();

        for (String row : this.currentOutputRows) {
            if (row.contains(name)) {
                String group = this.currentServerGroup != null ? " for server group '" + this.currentServerGroup + "'" : "";
                final DeploymentState[] statuses = DeploymentState.values();

                for (DeploymentState state : statuses) {
                    if (row.contains(state.getTitle())) {
                        log.info("Application deployment are state '" + name + "'->'"
                                + state.getTitle() + "'" + group + " Success");
                        return state;
                    }
                }
                log.warn("Status of application deployment not found! Do you call info command?\n"
                        + row);
                return UNKNOWN;
            }
        }
        throw new CommandFormatException("No result for " + name + " in \n" + String.join("\n",
                this.currentOutputRows));
    }
    // #### END   Method for checking without recalling command for applications deployments state

    // #### BEGIN Internal functionality method

    /**
     * Calling command in Cli, split output by lines and fill internal memory
     *
     * @param command Command for call
     * @return Readied output, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    private String callCommand(String command) {
        if (cli == null) {
            throw new IllegalStateException("Cli is not connected! Call connectCli method first!");
        }

        cli.sendLine(command);
        log.info("Called command: '" + command + "'");
        String output = cli.readOutput();
        if (output != null && !output.isEmpty()) {
            log.info("Read output:\n" + output);
            this.currentOutputRows = output.split("\n");
        } else {
            log.info("Read output: " + OUTPUT_EMPTY_MARK);
            this.currentOutputRows = new String[]{OUTPUT_EMPTY_MARK};
            return OUTPUT_EMPTY_MARK;
        }
        return output;
    }

    /**
     * Checking for state and existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private void checkMemory(String name) throws CommandFormatException, IOException {
        checkMemory(name, null);
    }

    /**
     * Checking for state and existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name         Represent name of application deployment for testing
     * @param invertSearch If you doesn't want to found application deployment set True, else default is false
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private void checkMemory(String name, boolean invertSearch) throws CommandFormatException, IOException {
        checkMemory(name, null, invertSearch);
    }

    /**
     * Checking for state and existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private void checkMemory(String name, DeploymentState expected) throws CommandFormatException, IOException {
        checkMemory(name, expected, false);
    }

    /**
     * Checking for state and existence of application deployment
     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
     * <p>
     * First call method 'readDeploymentX' or 'checkDeploymentX'!
     *
     * @param name         Represent name of application deployment for testing
     * @param expected     Expected state of application deployment
     * @param invertSearch If you doesn't want to found application deployment set True, else default is false
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private void checkMemory(String name, DeploymentState expected, boolean invertSearch) throws CommandFormatException, IOException {
        if (UNKNOWN.equals(expected)) {
            throw new CommandFormatException("Could not verify deployment state " + UNKNOWN + "!");
        }
        this.isOutputEmpty();

        for (String row : this.currentOutputRows) {

            if (row.contains(name)) {
                if (invertSearch) {
                    fail("Found non wanted application deployment " +
                            "" + name + " in \n" + String.join("\n",
                            this.currentOutputRows));
                }

                String group = this.currentServerGroup != null ? " for server group '" + this.currentServerGroup + "'" : "";
                if (expected == null) {
                    log.info("Check existence application deployment '" + name + "' Success");
                    return;
                } else if (row.contains(expected.getTitle())) {
                    log.info("Check application deployment in right state '" + name + "'->'"
                            + expected.getTitle() + "'" + group + " Success");
                    secondCheckByManagementCommands(name, expected, invertSearch);
                    return;
                }

                fail(name + " not in right state" + group + "! Expected '" + expected.getTitle()
                        + "' but is\n" + row);
            }
        }
        if (invertSearch) {
            log.info("Check non-existence application deployment '" + name + "' Success");
            secondCheckByManagementCommands(name, expected, invertSearch);
            return;
        }
        throw new CommandFormatException("No result for " + name + " in \n" + String.join("\n",
                this.currentOutputRows));
    }

    /**
     * Double checking for state and existence of application deployment
     * Checking by management command
     *
     * @param name         Represent name of application deployment for testing
     * @param expected     Expected state of application deployment
     * @param invertSearch If you doesn't want to found application deployment set True, else default is false
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private void secondCheckByManagementCommands(String name, DeploymentState expected, boolean invertSearch) throws CommandFormatException, IOException {
        if (ctx == null || expected == null) {
            return;
        }
        log.info("Double checking " + name + " with management command for state " + expected + "");

        if (ctx.isTerminated()) {
            log.error("FAILED: Could not double checking " + name + " with management command for state " + expected + "!" +
                    "Because connection to cli is closed!");
            return;
        }
        String serverGroup = this.currentServerGroup != null ? "/server-group=" + this.currentServerGroup : "";
        ModelNode mn = ctx.buildRequest(serverGroup + "/deployment=" + name + ":read-attribute(name=enabled)");
        ModelNode response = ctx.getModelControllerClient().execute(mn);
        if (response.hasDefined(OUTCOME) && response.get(OUTCOME).asString().equals(SUCCESS)) {

            assertThat("No result for " + name, response.hasDefined(RESULT), is(true));
            boolean enabled = invertSearch != mapBooleanByDeploymentStatus(expected);
            assertThat(name + " not in right state", response.get(RESULT).asBoolean(), is(enabled));

        } else if (response.hasDefined(OUTCOME) && response.get(OUTCOME).asString().equals(FAILED) && NOT_ADDED.equals(expected)) {

            assertThat("No result for " + name, response.hasDefined(FAILURE_DESCRIPTION), is(true));
            // Verify error message
            assertThat("Wrong error message for missing deployment " + name + " in server group " + this.currentServerGroup,
                    response.get(FAILURE_DESCRIPTION).asString(), allOf(
                            containsString("WFLYCTL0216: Management resource"),
                            containsString("not found"),
                            containsString(name),
                            containsString(this.currentServerGroup)));
        } else {
            throw new CommandFormatException("Invalid response for " + name);
        }
        log.info("Double checking " + name + " with management command for state " + expected + " - Success");
    }
    // #### END   Internal functionality method
}
