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

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.logging.Logger;

import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.UNKNOWN;

/**
 * Utils for verify state of applications deployments using command 'deployments list' and 'deployments info'.
 * Uses legacy and Aesh version of commands.
 * Direct verifying output of this commands.
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 * @since 26.1.18 15:06
 **/
public class DeploymentInfoUtils extends AbstractCliTestBase {

    private static final Logger log = Logger.getLogger(DeploymentInfoUtils.class);

    private String ipAddress;

    private String[] currentOutputRows;
    private String currentServerGroup;

    public DeploymentInfoUtils(String ipAddress) throws Exception {
        // initialize CLI Wrapper, because for testing require raw command output
        this.ipAddress = ipAddress;
    }

    public void connectCli() throws Exception {
        AbstractCliTestBase.initCLI(ipAddress);
    }

    public void disconnectCli() throws Exception {
        AbstractCliTestBase.closeCLI();
    }


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

        private String name;

        DeploymentState(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // #### BEGIN Internal state get method
    public String[] getMemmory() {
        return this.currentOutputRows;
    }

    public String getServerGroup() {
        return this.currentServerGroup;
    }
    // #### END   Internal state get method

    // #### BEGIN Direct checking methods
    public void checkDeploymentByList(String name) throws CommandFormatException {
        this.readDeploymentList();
        this.checkMemory(name);
    }

    /**
     * Target of this method is standalone test suit
     *
     * @param name
     * @param expected
     * @throws CommandFormatException
     */
    public void checkDeploymentByInfo(String name, DeploymentState expected) throws CommandFormatException {
        this.readDeploymentInfo();
        this.checkMemory(name, expected);
    }

    /**
     * Target of this method is domain test suit
     *
     * @param serverGroup
     * @param name
     * @param expected
     * @throws CommandFormatException
     */
    public void checkDeploymentByInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException {
        this.readDeploymentInfo(serverGroup);
        checkMemory(name, expected);
    }

    public void checkDeploymentByLegacyInfo(String name, DeploymentState expected) throws CommandFormatException {
        readLegacyDeploymentInfo();
        checkMemory(name, expected);
    }

    public void checkDeploymentByLegacyInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException {
        readLegacyDeploymentInfo(serverGroup);
        checkMemory(name, expected);
    }
    // #### END   Direct checking methods

    // #### BEGIN Public pre-loading methods
    public String readDeploymentList() {
        this.currentServerGroup = null;
        return callCommand("deployment list");
    }

    public void readDeploymentInfo() {
        this.readDeploymentInfo(null);
    }

    public String readDeploymentInfo(String serverGroup) {
        this.currentServerGroup = serverGroup;
        String groupPart = this.currentServerGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand("deployment info" + groupPart);
    }

    public void readLegacyDeploymentInfo() {
        this.readLegacyDeploymentInfo(null);
    }

    public String readLegacyDeploymentInfo(String serverGroup) {
        this.currentServerGroup = serverGroup;
        String groupPart = this.currentServerGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand("deployment-info" + groupPart);
    }
    // #### END   Public pre-loading methods

    // #### BEGIN Method for checking without recalling command for applications deployments state
    public void checkExistInOutputMemory(String name) throws CommandFormatException {
        checkMemory(name);
    }

    public void checkMissingInOutputMemory(String name) throws CommandFormatException {
        checkMemory(name, true);
    }

    public void checkExistInOutputMemory(String name, DeploymentState expected) throws CommandFormatException {
        checkMemory(name, expected);
    }

    public DeploymentState getStateByOutputMemory(String name) throws CommandFormatException {
        if (this.currentOutputRows == null || this.currentOutputRows.length <= 0)
            throw new CommandFormatException("No result to check! Call first info command!");

        for (String row : this.currentOutputRows) {
            if (row.contains(name)) {
                String group = this.currentServerGroup != null ? " for server group '" + this.currentServerGroup + "'" : "";
                final DeploymentState[] statuses = DeploymentState.values();

                for (DeploymentState state : statuses) {
                    if (row.contains(state.getName())) {
                        log.info("Application deployment are state '" + name + "'->'"
                                + state.getName() + "'" + group + " Success");
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
    private String callCommand(String command) {
        if (cli == null){
            throw new IllegalStateException("Cli is not connected! Call connectCli method first!");
        }

        cli.sendLine(command);
        log.info("Called command: '" + command + "'");
        String output = cli.readOutput();
        if (output != null && !output.isEmpty()) {
            log.info("Read output:\n" + output);
            this.currentOutputRows = output.split("\n");
        } else {
            log.info("Read output: --OUTPUT EMPTY--");
            this.currentOutputRows = new String[]{""};
        }
        return output;
    }

    private void checkMemory(String name) throws CommandFormatException {
        checkMemory(name, null);
    }

    private void checkMemory(String name, boolean invertSearch) throws CommandFormatException {
        checkMemory(name, null, invertSearch);
    }

    private void checkMemory(String name, DeploymentState expected) throws CommandFormatException {
        checkMemory(name, expected, false);
    }

    private void checkMemory(String name, DeploymentState expected, boolean invertSearch) throws CommandFormatException {
        if (this.currentOutputRows == null || this.currentOutputRows.length <= 0)
            throw new CommandFormatException("No result to check! Call first info or list command!");

        for (String row : this.currentOutputRows) {
            if (row.contains(name)) {
                if (invertSearch) {
                    throw new CommandFormatException("Found non wanted application deployment " +
                            "" + name + " in \n" + String.join("\n",
                            this.currentOutputRows));
                }
                String group = this.currentServerGroup != null ? " for server group '" + this.currentServerGroup + "'" : "";
                if (expected == null) {
                    log.info("Check existence application deployment '" + name + "' Success");
                    return;
                } else if (row.contains(expected.getName())) {
                    log.info("Check application deployment in right state '" + name + "'->'"
                            + expected.getName() + "'" + group + " Success");
                    return;
                }
                throw new CommandFormatException(name + " not in right state" + group + "! Expected '" + expected.getName()
                        + "' but is\n" + row);
            }
        }
        if (invertSearch) {
            log.info("Check non-existence application deployment '" + name + "' Success");
            return;
        }
        throw new CommandFormatException("No result for " + name + " in \n" + String.join("\n",
                this.currentOutputRows));
    }
    // #### END   Internal functionality method
}
