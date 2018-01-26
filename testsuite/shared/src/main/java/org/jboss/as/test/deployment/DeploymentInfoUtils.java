package org.jboss.as.test.deployment;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;

import java.io.IOException;

/**
 * @author Vratislav Marek (vmarek@redhat.com)
 * @since 26.1.18 15:06
 **/
public class DeploymentInfoUtils extends AbstractCliTestBase {

    private String[] currentOutputRows;

    public DeploymentInfoUtils() throws Exception {
        // initialize CLI Wrapper, because for testing require raw command output
        AbstractCliTestBase.initCLI(DomainTestSupport.masterAddress);
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

    public void checkDeploymentByList(String name) throws CommandFormatException, IOException {
        callCommand("deployment list");
        checkByList(name);
    }

    public void checkDeploymentByInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException {
        callCommand("deployment info --server-group=" + serverGroup);
        checkByList(name, expected);
    }

    public void checkDeploymentByLegacyInfo(String serverGroup, String name, DeploymentState expected) throws CommandFormatException {
        callCommand("deployment-info --server-group=" + serverGroup);
        checkByList(name, expected);
    }

    public void reCheckByList(String name) throws CommandFormatException {
        checkByList(name);
    }

    public void reCheckByList(String name, DeploymentState expected) throws CommandFormatException {
        checkByList(name, expected);
    }

    private void callCommand(String command) {
        cli.sendLine(command);
        String output = cli.readOutput();
        this.currentOutputRows = output.split("\n");
    }

    private void checkByList(String name) throws CommandFormatException {
        checkByList(name, null);
    }

    private void checkByList(String name, DeploymentState expected) throws CommandFormatException {
        if (this.currentOutputRows == null || this.currentOutputRows.length <= 0)
            throw new CommandFormatException("No result found!");

        for (String row : this.currentOutputRows) {
            if (row.contains(name)) {
                if (expected == null)
                    return;
                else if (row.contains(expected.getName())) {
                    return;
                }
                throw new CommandFormatException(name + " not in right state! Expected " + expected.getName() + "\n"
                        + row);
            }
        }
        throw new CommandFormatException("No result for " + name + " in \n" + String.join("\n",
                this.currentOutputRows));
    }
}
