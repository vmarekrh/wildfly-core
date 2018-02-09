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
 * Direct verifying output of this commands and verify it with management trusted command.
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 **/
public class DeploymentInfoUtils {
    private static final String OUTPUT_EMPTY_MARK = "--OUTPUT EMPTY--";
    private static final String FAILED = "failed";

    private static final Logger log = Logger.getLogger(DeploymentInfoUtils.class);

    private DeploymentInfoUtils() {
        //
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
                case STOPPED:
                    return false;
                case NOT_ADDED:
                case UNKNOWN:
                default:
                    throw new IllegalArgumentException("Unsupported state " + status + "!");
            }
        }
    }

    // #### BEGIN Internal containers classes

    /**
     * Represent container of called command deployment list/info.
     * Parsing command output for processing.
     * Hold additional information about processing verify check.
     */
    public static class DeploymentInfoResult {
        // Called command list or info about applications deployments for checking
        private final String command;
        // Selected server group in list/info command
        private final String serverGroup;
        // Original output of list/info command
        private final String originalOutput;
        /* Holding output of called command for multiple checking output without recall command
           Output is parsed for processing*/
        private final List<String> rows;
        // Represent request of management trusted command to verify check
        private String request;
        // Represent response of management trusted command to verify check
        private String response;

        private DeploymentInfoResult(String command, String serverGroup, String output) {
            this.command = command;
            this.serverGroup = serverGroup;
            this.originalOutput = output;
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
         *
         * @return If command output is empty return true, else If command has some output return false
         */
        public boolean isOutputEmpty() {
            if (this.rows == null || this.rows.size() <= 0) {
                throw new IllegalStateException("Internal memory corrupted! Could not be Null or empty!");
            }
            return this.rows.size() <= 1 && this.rows.contains(OUTPUT_EMPTY_MARK);
        }

        /**
         * Get parsed output for processing
         *
         * @return Parsed output for processing
         */
        public List<String> getRows() {
            return this.rows;
        }

        /**
         * Get called command list or info about applications deployments for checking
         *
         * @return Called command list or info about applications deployments for checking
         */
        public String getCommand() {
            return this.command;
        }

        /**
         * Return joined string of parsed rows or output empty mark in case output is empty
         *
         * @return Return joined string of parsed rows or output empty mark in case output is empty
         */
        @Override
        public String toString() {
            if (this.isOutputEmpty()) {
                return OUTPUT_EMPTY_MARK;
            }
            return String.join("\n", this.rows);
        }

        /**
         * If is set selected server group
         *
         * @return If server group has set return true, else false
         */
        public boolean hasServerGroup() {
            return this.serverGroup != null;
        }

        /**
         * Get selected server group in list/info command
         *
         * @return Selected server group in list/info command
         */
        public String getServerGroup() {
            return this.serverGroup;
        }

        /**
         * Get information message about selected server group.
         *
         * @return Information message about selected server group
         */
        public String getServerGroupInfo() {
            return this.serverGroup != null ? " for server group '" + this.serverGroup + "'" : "";
        }

        /**
         * Get original command output.
         *
         * @return Raw command output.
         */
        public String getOriginalOutput() {
            return this.originalOutput;
        }

        // &&&& BEGIN Method for additional information about processing verify check

        /**
         * Additional information about processing verify check.
         *
         * @param request Request of management trusted command to verify check.
         */
        public void setRequest(String request) {
            this.request = request;
            this.response = null;
        }

        /**
         * Additional information about processing verify check.
         *
         * @return Request of management trusted command to verify check.
         */
        public String getRequest() {
            return this.request;
        }

        /**
         * Additional information about processing verify check.
         *
         * @param response Response of management trusted command to verify check.
         */
        public void setResponse(String response) {
            this.response = response;
        }

        /**
         * Additional information about processing verify check.
         *
         * @return Response of management trusted command to verify check.
         */
        public String getResponse() {
            return this.response;
        }
    }

    /**
     * Container of Utils internal parameters, reduce of overloaded function of check
     */
    private static class InternalParameters {
        private enum SearchType {
            // Represent searching for status of application deployment
            STATUS
            // Represent searching for deployed application deployment
            , EXIST
            // Represent searching for non-deployed application deployment
            , MISSING
        }

        // Represent result of list of info command, is required for checking
        private final DeploymentInfoResult result;
        // Represent application deployment name
        private String name;
        // Expected state of application deployment for check
        private DeploymentState expectedState;
        private SearchType searchType;
        private CommandContext ctx;
        // Represent string holder for goal of checking to log/error messages
        private String goalStr;

        private InternalParameters(DeploymentInfoResult result) {
            if (result == null) {
                throw new IllegalArgumentException("Could not set null command result!");
            }
            this.result = result;
            this.name = null;
            this.expectedState = UNKNOWN;
            this.searchType = SearchType.EXIST;
            this.ctx = null;
            this.goalStr = null;
        }

        private DeploymentInfoResult getResult() {
            return result;
        }

        private String getName() {
            return name;
        }

        /**
         * @param name Represent name of application deployment for testing
         * @return Return self, for builder chain
         */
        private InternalParameters setName(String name) {
            this.name = name;
            return this;
        }

        private DeploymentState getExpectedState() {
            return expectedState;
        }

        /**
         * @param expectedState Expected state of application deployment
         * @return Return self, for builder chain
         */
        private InternalParameters setExpectedState(DeploymentState expectedState) {
            this.expectedState = expectedState != null ? expectedState : UNKNOWN;
            this.searchType = UNKNOWN.equals(this.expectedState) ? SearchType.EXIST : SearchType.STATUS;
            return this;
        }

        private boolean isSearchTypeStatus() {
            return SearchType.STATUS.equals(this.searchType);
        }

        private boolean isSearchTypeExist() {
            return SearchType.EXIST.equals(this.searchType);
        }

        private boolean isSearchTypeMissing() {
            return SearchType.MISSING.equals(this.searchType);
        }

        private InternalParameters setSearchType(SearchType searchType) {
            this.searchType = searchType;
            this.goalStr = null;
            return this;
        }

        private CommandContext getCtx() {
            return ctx;
        }

        private InternalParameters setCtx(CommandContext ctx) {
            this.ctx = ctx;
            return this;
        }

        private boolean isOutputEmpty() {
            return this.result.isOutputEmpty();
        }

        private List<String> getRows() {
            return this.result.getRows();
        }

        /**
         * Lazy loaded cashed message of checking goal to log/error
         *
         * @return Goal message
         */
        private String getGoalStr() {
            if (this.goalStr == null) {
                this.goalStr = this.isSearchTypeStatus() ? " state " + this.getExpectedState() :
                        this.isSearchTypeExist() ? " existing in deployment" :
                                this.isSearchTypeMissing() ? " missing in deployment" :
                                        " UNKNOWN GOAL";
            }
            return this.goalStr;
        }

        /**
         * Overload to debugging errors
         *
         * @return All information about verification, called parameters and results
         */
        @Override
        public String toString() {
            return "InternalParameters{\n" +
                    "result={\n called_command='" + result.getCommand() + "'" +
                    "\n, server_group='" + result.getServerGroup() + "'" +
                    "\n, command_output={\n" + result.getOriginalOutput() + "\n}" +
                    "\n, request='" + result.getRequest() + "'" +
                    "\n, response={\n" + result.getResponse() + "\n}" +
                    "\n}\n, name='" + name + '\'' +
                    "\n, expectedState=" + expectedState +
                    "\n, searchType=" + searchType +
                    "\n, ctx=" + ctx +
                    "\n, goalStr='" + goalStr + '\'' +
                    "\n}";
        }
    }

    // #### END   Internal containers classes

    // #### BEGIN Public pre-loading methods

    /**
     * Read information from command 'deployment List'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function without expectedState state
     *
     * @param cli CLIWrapper to cli connection and collect raw command output
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static DeploymentInfoResult deploymentList(CLIWrapper cli) {
        return callCommand(cli, "deployment list", null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expectedState state
     * For standalone mode.
     *
     * @param cli CLIWrapper to cli connection and collect raw command output
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static DeploymentInfoResult deploymentInfo(CLIWrapper cli) {
        return deploymentInfo(cli, null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expectedState state
     * For domain mode.
     *
     * @param cli         CLIWrapper to cli connection and collect raw command output
     * @param serverGroup Selected server group in list/info command
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static DeploymentInfoResult deploymentInfo(CLIWrapper cli, String serverGroup) {
        String groupPart = serverGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand(cli, "deployment info" + groupPart, serverGroup);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expectedState state
     * For standalone mode.
     * Using Legacy command.
     *
     * @param cli CLIWrapper to cli connection and collect raw command output
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static DeploymentInfoResult legacyDeploymentInfo(CLIWrapper cli) {
        return legacyDeploymentInfo(cli, null);
    }

    /**
     * Read information from command 'deployment info'
     * Save output to internal memory to checking application deployments.
     * After this you can start call checking function with expectedState state
     * For domain mode.
     * Using Legacy command.
     *
     * @param cli         CLIWrapper to cli connection and collect raw command output
     * @param serverGroup Selected server group in list/info command
     * @return Return output of command, if is output empty return {@value OUTPUT_EMPTY_MARK} mark
     */
    public static DeploymentInfoResult legacyDeploymentInfo(CLIWrapper cli, String serverGroup) {
        String groupPart = serverGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand(cli, "deployment-info" + groupPart, serverGroup);
    }
    // #### END   Public pre-loading methods

    // #### BEGIN Method for checking without recalling command for applications deployments state

    /**
     * Checking for existence of application deployment
     *
     * @param result Result of called list/info command requested for checking
     * @param name   Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name) throws CommandFormatException, IOException {
        check(new InternalParameters(result).setName(name));
    }

    /**
     * Checking for existence of application deployment
     *
     * @param result Result of called list/info command requested for checking
     * @param name   Represent name of application deployment for testing
     * @param ctx    Represent CommandContext to cli connection and handle management commands
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(new InternalParameters(result).setName(name).setCtx(ctx));
    }

    /**
     * Checking for state and existence of application deployment
     *
     * @param result   Result of called list/info command requested for checking
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name, DeploymentState expected) throws CommandFormatException, IOException {
        check(new InternalParameters(result).setName(name).setExpectedState(expected));
    }

    /**
     * Checking for state and existence of application deployment
     *
     * @param result   Result of called list/info command requested for checking
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @param ctx      Represent CommandContext to cli connection and handle management commands
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name, DeploymentState expected, CommandContext ctx) throws CommandFormatException, IOException {
        check(new InternalParameters(result).setName(name).setExpectedState(expected).setCtx(ctx));
    }

    /**
     * Checking for non existence of application deployment
     *
     * @param result Result of called list/info command requested for checking
     * @param name   Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkMissing(DeploymentInfoResult result, String name) throws CommandFormatException, IOException {
        check(new InternalParameters(result).setName(name).setSearchType(InternalParameters.SearchType.MISSING));
    }

    /**
     * Checking for non existence of application deployment.
     *
     * @param result Result of called list/info command requested for checking.
     * @param name   Represent name of application deployment for testing.
     * @param ctx    Represent CommandContext to cli connection and handle management commands
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkMissing(DeploymentInfoResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(new InternalParameters(result).setName(name).setSearchType(InternalParameters.SearchType.MISSING).setCtx(ctx));
    }

    /**
     * Checking for empty installed deployments.
     *
     * @param result Result of called list/info command requested for checking.
     */
    public static void checkVoid(DeploymentInfoResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Cant check null to void!");
        }
        assertThat("Command output contains some deployments! Checking a void FAILED", result.isOutputEmpty(), is(true));
    }

    /**
     * In case you need know state of application deployment
     *
     * @param result Result of called list/info command requested for checking
     * @param name   Name of application deployment
     * @return State of application deployment, if not found return UNKNOWN state
     * @throws CommandFormatException
     */
    public static DeploymentState getStateByOutputMemory(DeploymentInfoResult result, String name) throws CommandFormatException {
        result.isOutputEmpty();

        for (String row : result.getRows()) {
            if (row.contains(name)) {
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
    private static DeploymentInfoResult callCommand(CLIWrapper cli, String command, String serverGroup) {
        if (cli == null) {
            throw new IllegalStateException("Cli is not connected! Call connectCli method first!");
        }

        cli.sendLine(command);
        log.info("Called command: '" + command + "'");
        return new DeploymentInfoResult(command, serverGroup, cli.readOutput());
    }

    /**
     * Checking for state or (non)existence of application deployment.
     *
     * @param param Containers of parameters, because loot of optional parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void check(InternalParameters param) throws CommandFormatException, IOException {
        if (param.isSearchTypeStatus() && UNKNOWN.equals(param.getExpectedState())) {
            throw new IllegalStateException("Could not verify deployment state " + UNKNOWN + "!");
        }

        if (!param.isOutputEmpty()) {
            for (String row : param.getRows()) {
                if (row.contains(param.getName())) {
                    if (param.isSearchTypeMissing()) {

                        fail("Found non wanted application deployment " +
                                "" + param.getName() + " in \n" + param.getResult());
                    } else if (param.isSearchTypeExist()) {

                        log.info("Check existence application deployment '" + param.getName() + "' Success");
                        verifyCheck(param);
                        return;
                    } else if (param.isSearchTypeStatus()) {

                        assertThat("", row, containsString(param.getExpectedState().getTitle()));
                        log.info("Check application deployment in right state '" + param.getName() + "'->'"
                                + param.getExpectedState().getTitle() + " by command '" + param.getResult().getCommand() + " Success");
                        verifyCheck(param);
                        return;
                    }

                    fail(param.getName() + " not in right state" + param.getResult().getServerGroupInfo() +
                            "! Expected '" + param.getExpectedState().getTitle() + "' but is\n" + row);
                }
            }
        }
        if (param.isSearchTypeMissing()) {
            log.info("Check non-existence application deployment '" + param.getName() + "' Success");
            verifyCheck(param);
            return;
        }
        throw new CommandFormatException("No result for " + param.getName() + " in \n" + param.getResult());
    }

    /**
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management trusted command.
     *
     * @param param Containers of parameters, because loot of optional parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void verifyCheck(InternalParameters param) throws CommandFormatException, IOException {
        if (param.getCtx() == null) {
            log.warn("Skip double checking by management trusted commands - CommandContext connection not set!");
            return;
        }
        if (param.isSearchTypeStatus() && UNKNOWN.equals(param.getExpectedState())) {
            throw new IllegalStateException("Could not verify deployment state " + UNKNOWN + "!");
        }

        log.info("Double checking " + param.getName() + " with management command trusted for " + param.getGoalStr());

        if (param.getCtx().isTerminated()) {
            throw new IllegalStateException("FAILED: Could not double checking " + param.getName() +
                    " with management trusted command for " + param.getGoalStr() + "!" + "Because connection to cli is closed!");
        }

        if (param.isSearchTypeStatus()) {
            verifyCheckStatus(param);
        } else if (param.isSearchTypeExist()) {
            verifyCheckExist(param);
        } else if (param.isSearchTypeMissing()) {
            verifyCheckMissing(param);
        } else {
            throw new IllegalStateException("Unknown operation selected! Could not verify check!");
        }

        log.info("Double checking " + param.getName() + " with management trusted command for " + param.getGoalStr() + " - Success");
    }

    /**
     * Concentration of verify status of application deployment.
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management trusted command.
     *
     * @param param Containers of parameters, because loot of optional parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void verifyCheckStatus(InternalParameters param) throws CommandFormatException, IOException {
        String serverGroupStr = param.getResult().hasServerGroup() ? "/server-group=" + param.getResult().getServerGroup() : "";
        param.getResult().setRequest(serverGroupStr + "/deployment=" + param.getName() + ":read-attribute(name=enabled)");
        ModelNode mn = param.getCtx().buildRequest(param.getResult().getRequest());
        ModelNode response = param.getCtx().getModelControllerClient().execute(mn);
        param.getResult().setResponse(response.asString());

        // State NOT_ADDED is not supported by management command
        if (!NOT_ADDED.equals(param.getExpectedState())) {
            // Standard verify with boolean enabled/disabled
            assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
            assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                    response.get(OUTCOME).asString(), is(SUCCESS));
            boolean enable = mapBooleanByDeploymentStatus(param.getExpectedState());
            assertThat("No result for " + param.getName(), response.hasDefined(RESULT), is(true));
            assertThat(param.getName() + " not in right state", response.get(RESULT).asBoolean(), is(enable));
        } else {
            // Verify state NOT_ADDED, because is only in Domain mode, for domain mode not-exist deployment in other group
            assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
            assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                    response.get(OUTCOME).asString(), is(FAILED));
            assertThat("No result for " + param.getName(), response.hasDefined(FAILURE_DESCRIPTION), is(true));
            // Verify error message
            assertThat("Wrong error message for missing deployment " + param.getName() + " in server group " + param.getResult().getServerGroup(),
                    response.get(FAILURE_DESCRIPTION).asString(), allOf(
                            containsString("WFLYCTL0216: Management resource"),
                            containsString("not found"),
                            containsString(param.getName())
                    )
            );
        }
    }

    /**
     * Concentration of verify existence application deployment.
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management trusted command.
     *
     * @param param Containers of parameters, because loot of optional parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void verifyCheckExist(InternalParameters param) throws CommandFormatException, IOException {
        param.getResult().setRequest("/deployment=" + param.getName() + ":read-attribute(name=name)");
        ModelNode mn = param.getCtx().buildRequest(param.getResult().getRequest());
        ModelNode response = param.getCtx().getModelControllerClient().execute(mn);
        param.getResult().setResponse(response.asString());

        assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
        assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                response.get(OUTCOME).asString(), is(SUCCESS));
        assertThat("No result for " + param.getName(), response.hasDefined(RESULT), is(true));
        assertThat(param.getName() + " not in right state", response.get(RESULT).asString(), is(param.getName()));
    }

    /**
     * Concentration of verify non-existence application deployment.
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management trusted command.
     *
     * @param param Containers of parameters, because loot of optional parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void verifyCheckMissing(InternalParameters param) throws CommandFormatException, IOException {
        param.getResult().setRequest("/deployment=" + param.getName() + ":read-attribute(name=name)");
        ModelNode mn = param.getCtx().buildRequest(param.getResult().getRequest());
        ModelNode response = param.getCtx().getModelControllerClient().execute(mn);
        param.getResult().setResponse(response.asString());

        assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
        assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                response.get(OUTCOME).asString(), is(FAILED));
        assertThat("No result for " + param.getName(), response.hasDefined(FAILURE_DESCRIPTION), is(true));
        // Verify error message
        assertThat("Wrong error message for missing deployment " + param.getName() + " in server group " + param.getResult().getServerGroup(),
                response.get(FAILURE_DESCRIPTION).asString(), allOf(
                        containsString("WFLYCTL0216: Management resource"),
                        containsString("not found"),
                        containsString(param.getName())
                )
        );
    }
    // #### END   Internal functionality method
}
