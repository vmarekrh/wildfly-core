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
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.common.annotation.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.core.AllOf.allOf;
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
public class DeploymentInfoUtils {
    private static final String OUTPUT_EMPTY_MARK = "--OUTPUT EMPTY--";
    private static final String FAILED = "failed";

    private static final Logger log = Logger.getLogger(DeploymentInfoUtils.class);

//    // In case you disabled double checking, this variable is for enable double checking without parameter
//    private CommandContext disabledCtx;
//    private CommandContext ctx;

//    // Holding output of last time called command for multiple checking output without recall command
//    private String[] currentOutputRows;
//    // Server group name in last time called command
//    private String currentServerGroup;

//    public DeploymentInfoUtils(String ipAddress) {
//        // initialize CLI Wrapper, because for testing require raw command output
//        this.ipAddress = ipAddress;
//    }

    private DeploymentInfoUtils() {
        //
    }

//    /**
//     * Before you start checking, do not forget to connect to cli session
//     *
//     * @throws Exception
//     */
//    public void connectCli() throws Exception {
//        AbstractCliTestBase.initCLI(ipAddress);
//    }
//
//    /**
//     * After testing do not forget do close cli session
//     *
//     * @throws Exception
//     */
//    public void disconnectCli() throws Exception {
//        AbstractCliTestBase.closeCLI();
//        this.resetDoubleCheck();
//    }
//
//    /**
//     * Enabling double checking
//     * Use pre enabled {@link CommandContext} cli session
//     */
//    public void enableDoubleCheck() {
//        enableDoubleCheck(this.disabledCtx);
//    }

//    /**
//     * Enabling double checking
//     * For use double checking state of application deployment by management command you must se CommandContext
//     *
//     * @param ctx {@link CommandContext} cli session
//     */
//    public void enableDoubleCheck(CommandContext ctx) {
//        if (ctx == null) {
//            throw new IllegalStateException("Could not accept null for enable double checking!");
//        }
//        if (ctx.isTerminated())
//            throw new IllegalStateException("Could not accept closed cli for enable double checking!");
//        this.resetDoubleCheck();
//        this.ctx = ctx;
//    }
//
//    /**
//     * Disabling double checking
//     */
//    public void disableDoubleCheck() {
//        this.disabledCtx = this.ctx;
//        this.ctx = null;
//    }
//
//    /**
//     * Disable double checking and discard saved {@link CommandContext} cli session
//     */
//    public void resetDoubleCheck() {
//        this.ctx = null;
//        this.disabledCtx = null;
//    }

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
                case UNKNOWN:
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

    public static class CommandResult {

        private final String command;
        private final String serverGroup;
        private final String originalCommandResult;
        // Holding output of last time called command for multiple checking output without recall command
        private final List<String> rows;

        private CommandResult(String command, String serverGroup, String output) {
            this.command = command;
            this.serverGroup = serverGroup;
            this.originalCommandResult = output;
            if (output != null && !output.isEmpty()) {
                log.info("Read output:\n" + output);
                this.rows = Arrays.asList(output.split("\n"));
            } else {
                log.info("Read output: " + OUTPUT_EMPTY_MARK);
                this.rows = new ArrayList<>();
                this.rows.add(OUTPUT_EMPTY_MARK);
            }
        }

        /**
         * Checking if called command has empty output.
         * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
         * <p>
         * First call method 'readDeploymentX' or 'checkDeploymentX'!
         *
         * @return If command output is empty return true, else If command has some output return false
         * @throws IllegalStateException If you don't call info method! Nothing to check!
         */
        public boolean isOutputEmpty() {
            if (this.rows == null || this.rows.size() <= 0) {
                throw new IllegalStateException("Internal memory corrupted! Could not be Null or empty!");
            }
            return this.rows.size() <= 1 && this.rows.contains(OUTPUT_EMPTY_MARK);
        }

        public List<String> getRows() {
            return this.rows;
        }

        public Iterator<String> getIterator() {
            this.isOutputEmpty();

            return this.rows.iterator();
        }

        public String getCommand() {
            return this.command;
        }

        @Override
        public String toString() {
            if (this.isOutputEmpty()) {
                return OUTPUT_EMPTY_MARK;
            }
            return String.join("\n", this.rows);
        }

        public String getServerGroup() {
            return this.serverGroup;
        }

        public String getServerGroupInfo() {
            return this.serverGroup != null ? " for server group '" + this.serverGroup + "'" : "";
        }

        public String getOriginalCommandResult() {
            return this.originalCommandResult;
        }
    }

    // #### BEGIN Direct checking methods
//    public void checkDeploymentByList(String name) throws CommandFormatException, IOException {
//        this.deploymentList();
//        this.check(name);
//    }

//    /**
//     * Call command to get information about application deployment and checking for his state and existence
//     * For standalone mode.
//     *
//     * @param name     Represent name of application deployment for testing
//     * @param expected Expected state of application deployment
//     * @throws CommandFormatException Throw in case of assert failure
//     * @throws IOException            Throw in case of problem with execute management command
//     */
//    public void checkDeploymentByInfo(String name, DeploymentState expected) throws CommandFormatException, IOException {
//        this.deploymentInfo();
//        this.check(name, expected);
//    }

//    /**
//     * Call command to get information about application deployment and checking for his state and existence
//     * For domain mode.
//     *
//     * @param serverGroup Server group in case of domain mode run
//     * @param name        Represent name of application deployment for testing
//     * @param expected    Expected state of application deployment
//     * @throws CommandFormatException Throw in case of assert failure
//     * @throws IOException            Throw in case of problem with execute management command
//     */
//    public void checkDeploymentByInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException, IOException {
//        this.deploymentInfo(serverGroup);
//        check(name, expected);
//    }

//    /**
//     * Call command to get information about application deployment and checking for his state and existence
//     * For standalone mode.
//     * Using Legacy command.
//     *
//     * @param name     Represent name of application deployment for testing
//     * @param expected Expected state of application deployment
//     * @throws CommandFormatException Throw in case of assert failure
//     * @throws IOException            Throw in case of problem with execute management command
//     */
//    public void checkDeploymentByLegacyInfo(String name, DeploymentState expected) throws CommandFormatException, IOException {
//        legacyDeploymentInfo();
//        check(name, expected);
//    }

//    /**
//     * Call command to get information about application deployment and checking for his state and existence
//     * For domain mode.
//     * Using Legacy command.
//     *
//     * @param serverGroup Server group in case of domain mode run
//     * @param name        Represent name of application deployment for testing
//     * @param expected    Expected state of application deployment
//     * @throws CommandFormatException Throw in case of assert failure
//     * @throws IOException            Throw in case of problem with execute management command
//     */
//    public void checkDeploymentByLegacyInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException, IOException {
//        legacyDeploymentInfo(serverGroup);
//        check(name, expected);
//    }
    // #### END   Direct checking methods

    // #### BEGIN Public pre-loading methods

    /**
     * Read information from command 'deployment List'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function without expected state
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static CommandResult deploymentList(CLIWrapper cli) {
        return callCommand(cli, "deployment list", null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expected state
     * For standalone mode.
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static CommandResult deploymentInfo(CLIWrapper cli) {
        return deploymentInfo(cli, null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expected state
     * For domain mode.
     *
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static CommandResult deploymentInfo(CLIWrapper cli, String serverGroup) {
        String groupPart = serverGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand(cli, "deployment info" + groupPart, serverGroup);
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
    public static CommandResult legacyDeploymentInfo(CLIWrapper cli) {
        return legacyDeploymentInfo(cli, null);
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
    public static CommandResult legacyDeploymentInfo(CLIWrapper cli, String serverGroup) {
        String groupPart = serverGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand(cli, "deployment-info" + groupPart, serverGroup);
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
    public static void checkExist(CommandResult result, String name) throws CommandFormatException, IOException {
        check(result, name);
    }

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
    public static void checkExist(CommandResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(result, name, ctx);
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
    public static void checkExist(CommandResult result, String name, DeploymentState expected) throws CommandFormatException, IOException {
        check(result, name, expected);
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
    public static void checkExist(CommandResult result, String name, DeploymentState expected, CommandContext ctx) throws CommandFormatException, IOException {
        check(result, name, expected, ctx);
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
    public static void checkMissing(CommandResult result, String name) throws CommandFormatException, IOException {
        check(result, name, true);
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
    public static void checkMissing(CommandResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(result, name, true, ctx);
    }

    public static void checkVoid(CommandResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Cant check null to void!");
        }
        assertThat("Command output contains some deployments! Checking a void FAILED", result.isOutputEmpty(), is(true));
    }

//    /**
//     * Checking if called command has empty output.
//     * Checking in pre loaded memory, for reduce called command in cli for more asserts for one command call
//     * <p>
//     * First call method 'readDeploymentX' or 'checkDeploymentX'!
//     *
//     * @return If command output is empty return true, else If command has some output return false
//     * @throws CommandFormatException If you don't call info method! Nothing to check!
//     */
//    public boolean isOutputEmpty() throws CommandFormatException {
//        if (this.currentOutputRows == null || this.currentOutputRows.length <= 0) {
//            throw new CommandFormatException("Error: Nothing to check! Call first info command!");
//        }
//        return this.currentOutputRows.length <= 1 && this.currentOutputRows[0].contains(OUTPUT_EMPTY_MARK);
//    }

    /**
     * In case you need know state of application deployment
     *
     * @param name Name of application deployment
     * @return State of application deployment, if not found return UNKNOWN state
     * @throws CommandFormatException
     */
    public static DeploymentState getStateByOutputMemory(CommandResult result, String name) throws CommandFormatException {
        result.isOutputEmpty();

        for (String row : result.getRows()) {
            if (row.contains(name)) {
                //String group = this.currentServerGroup != null ? " for server group '" + this.currentServerGroup + "'" : "";
                final DeploymentState[] statuses = DeploymentState.values();

                for (DeploymentState state : statuses) {
                    if (row.contains(state.getTitle())) {
                        log.info("Application deployment are state '" + name + "'->'"
                                + state.getTitle() + " by command '" + result.getCommand() + " Success");
                        return state;
                    }
                }
                log.warn("Status of application deployment not found! Do you call info command?\n"
                        + row);
                return UNKNOWN;
            }
        }
        throw new CommandFormatException("No result for " + name + " in \n" + result);
    }
    // #### END   Method for checking without recalling command for applications deployments state

    // #### BEGIN Internal functionality method

    /**
     * Calling command in Cli, split output by lines and fill internal memory
     *
     * @param command Command for call
     * @return Readied output, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    private static CommandResult callCommand(CLIWrapper cli, String command, String serverGroup) {
        if (cli == null) {
            throw new IllegalStateException("Cli is not connected! Call connectCli method first!");
        }

        cli.sendLine(command);
        log.info("Called command: '" + command + "'");
        return new CommandResult(command, serverGroup, cli.readOutput());
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
    private static void check(CommandResult result, String name) throws CommandFormatException, IOException {
        check(result, name, null, null);
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
    private static void check(CommandResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(result, name, null, ctx);
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
    private static void check(CommandResult result, String name, boolean invertSearch) throws CommandFormatException, IOException {
        check(result, name, null, invertSearch, null);
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
    private static void check(CommandResult result, String name, boolean invertSearch, CommandContext ctx) throws CommandFormatException, IOException {
        check(result, name, null, invertSearch, ctx);
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
    private static void check(CommandResult result, String name, DeploymentState expected) throws CommandFormatException, IOException {
        check(result, name, expected, false, null);
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
    private static void check(CommandResult result, String name, DeploymentState expected, CommandContext ctx) throws CommandFormatException, IOException {
        check(result, name, expected, false, ctx);
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
    private static void check(CommandResult result, String name, DeploymentState expected, boolean invertSearch, CommandContext ctx) throws CommandFormatException, IOException {
        if (UNKNOWN.equals(expected)) {
            throw new CommandFormatException("Could not verify deployment state " + UNKNOWN + "!");
        }

        if (!result.isOutputEmpty()) {
            for (String row : result.getRows()) {

                if (row.contains(name)) {
                    if (invertSearch) {
                        fail("Found non wanted application deployment " +
                                "" + name + " in \n" + result);
                    }

                    if (expected == null) {
                        log.info("Check existence application deployment '" + name + "' Success");
                        return;
                    } else if (row.contains(expected.getTitle())) {
                        log.info("Check application deployment in right state '" + name + "'->'"
                                + expected.getTitle() + " by command '" + result.getCommand() + " Success");
                        checkManagement(result, name, expected, invertSearch, ctx);
                        return;
                    }

                    fail(name + " not in right state" + result.getServerGroupInfo() + "! Expected '" + expected.getTitle()
                            + "' but is\n" + row);
                }
            }
        }
        if (invertSearch) {
            log.info("Check non-existence application deployment '" + name + "' Success");
            checkManagement(result, name, expected, invertSearch, ctx);
            return;
        }
        throw new CommandFormatException("No result for " + name + " in \n" + result);
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
    private static void checkManagement(CommandResult result, String name, DeploymentState expected, boolean invertSearch, CommandContext ctx) throws CommandFormatException, IOException {
        if (ctx == null) {
            return;
        }
        expected = expected == null ? UNKNOWN : expected;
        log.info("Double checking " + name + " with management command for state " + expected + "");

        if (ctx.isTerminated()) {
            log.error("FAILED: Could not double checking " + name + " with management command for state " + expected + "!" +
                    "Because connection to cli is closed!");
            return;
        }
        String serverGroup = result.getServerGroup() != null ? "/server-group=" + result.getServerGroup() : "";
        ModelNode mn = ctx.buildRequest(serverGroup + "/deployment=" + name + ":read-attribute(name=enabled)");
        ModelNode response = ctx.getModelControllerClient().execute(mn);
        if (response.hasDefined(OUTCOME)) {
            if (response.get(OUTCOME).asString().equals(SUCCESS)) {

                assertThat("No result for " + name, response.hasDefined(RESULT), is(true));
                boolean enabled = invertSearch != mapBooleanByDeploymentStatus(expected);
                assertThat(name + " not in right state", response.get(RESULT).asBoolean(), is(enabled));

            } else if (response.get(OUTCOME).asString().equals(FAILED) && (NOT_ADDED.equals(expected) || UNKNOWN.equals(expected))) {

                assertThat("No result for " + name, response.hasDefined(FAILURE_DESCRIPTION), is(true));
                // Verify error message
                assertThat("Wrong error message for missing deployment " + name + " in server group " + result.getServerGroup(),
                        response.get(FAILURE_DESCRIPTION).asString(), allOf(
                                containsString("WFLYCTL0216: Management resource"),
                                containsString("not found"),
                                containsString(name),
                                containsString(result.getServerGroup())));
            } else {
                throw new CommandFormatException("Unknown response for " + name);
            }
        } else {
            throw new CommandFormatException("Invalid response for " + name);
        }

        log.info("Double checking " + name + " with management command for state " + expected + " - Success");
    }
    // #### END   Internal functionality method
}
