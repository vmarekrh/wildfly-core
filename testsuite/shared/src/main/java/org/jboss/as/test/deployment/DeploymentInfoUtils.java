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

    private String[] currentOutputRows;
    private String currentServerGroup;

    public DeploymentInfoUtils(String ipAddress) throws Exception {
        // initialize CLI Wrapper, because for testing require raw command output
        AbstractCliTestBase.initCLI(ipAddress);
    }

    public enum DeploymentState {
        ENABLED("enabled"), ADDED("added"), NOT_ADDED("not added");

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
    public void readDeploymentList() {
        this.currentServerGroup = null;
        callCommand("deployment list");
    }

    public void readDeploymentInfo() {
        this.readDeploymentInfo(null);
    }

    public void readDeploymentInfo(String serverGroup) {
        this.currentServerGroup = serverGroup;
        String groupPart = this.currentServerGroup != null ? " --server-group=" + serverGroup : "";
        callCommand("deployment info" + groupPart);
    }

    public void readLegacyDeploymentInfo() {
        this.readLegacyDeploymentInfo(null);
    }

    public void readLegacyDeploymentInfo(String serverGroup) {
        this.currentServerGroup = serverGroup;
        String groupPart = this.currentServerGroup != null ? " --server-group=" + serverGroup : "";
        callCommand("deployment-info" + groupPart);
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
    // #### END   Method for checking without recalling command for applications deployments state

    // #### BEGIN Internal functionality method
    private void callCommand(String command) {
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
            throw new CommandFormatException("No result to check!");

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
