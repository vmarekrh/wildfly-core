/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.deployment;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.jboss.as.test.deployment.DeploymentInfoUtils.LINE_SEPARATOR;

/**
 * It is adapter of DeploymentInfoUtils for handling cli and output of commands.
 * Encapsulation of CommandContext or CLIWrapper.
 * You can use what you have.
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 **/
public class DeploymentInfoReader {


    private final ByteArrayOutputStream outputStream;
    private final ByteArrayOutputStream errorStream;
    private final CommandContext ctx;
    private final CLIWrapper cli;
    private final boolean basic;

    private String lastCommand = null;
    private String outBuffer = null;
    private String errBuffer = null;
    private boolean hasErrorBuffer = false;

    public DeploymentInfoReader() throws CommandLineException {
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        errorStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorStream));

        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setInitConsole(true).setConsoleInput(System.in).setConsoleOutput(outputStream).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();
        basic = true;

        // Disabled unused attributes
        this.cli = null;
    }

    public DeploymentInfoReader(CommandContext ctx) {
        this(ctx, null);
    }


    public DeploymentInfoReader(CommandContext ctx, ByteArrayOutputStream outputStream) {
        this(ctx, outputStream, new ByteArrayOutputStream());
        System.setErr(new PrintStream(errorStream));
    }

    public DeploymentInfoReader(CommandContext ctx, ByteArrayOutputStream outputStream, ByteArrayOutputStream errorStream) {
        if (ctx == null) {
            throw new IllegalArgumentException("Couldn't create instance DeploymentInfoReader with null CommandContext!");
        }
        if (errorStream == null) {
            throw new IllegalArgumentException("Couldn't create instance DeploymentInfoReader with null ErrorStream!");
        }
        this.ctx = ctx;

        if (outputStream == null) {
            this.outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(this.outputStream);
            System.setOut(printStream);

            this.ctx.captureOutput(printStream);
        } else {
            this.outputStream = outputStream;
        }
        this.errorStream = errorStream;
        basic = true;

        // Disabled unused attributes
        this.cli = null;
    }

    public DeploymentInfoReader(CLIWrapper cli) {
        if (cli == null) {
            throw new IllegalArgumentException("Couldn't create instance DeploymentInfoReader with null CLIWrapper!");
        }
        this.cli = cli;
        ctx = this.cli.getCommandContext();
        errorStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorStream));
        basic = false;

        // Disabled unused attributes
        outputStream = null;
    }

    public ModelNode buildRequestAndExecute(String request) throws CommandFormatException, IOException {
        if (!isReady()) {
            throw new IllegalStateException("DeploymentInfoReader is not connected to Cli!");
        }
        final ModelNode modelNode = ctx.buildRequest(request);
        return ctx.getModelControllerClient().execute(modelNode);
    }

    public boolean isReady() {
        if (basic) {
            return !ctx.isTerminated() && ctx.getModelControllerClient() != null;
        } else {
            return !ctx.isTerminated() && cli.isConnected();
        }
    }

    public void sendLine(String command) {
        if (!isReady()) {
            throw new IllegalStateException("DeploymentInfoReader is not connected to Cli!");
        }
        lastCommand = command;
        outBuffer = null;
        errBuffer = null;
        hasErrorBuffer = false;
        if (basic) {
            ccSendLine(command);
        } else {
            cwSendLine(command);
        }
    }

    public String getLastCommand() {
        return lastCommand;
    }

    public String readOutput() {
        return outBuffer;
    }

    public boolean hasError() {
        return hasErrorBuffer;
    }

    public String readErrorOutput() {
        return errBuffer;
    }

    private void ccSendLine(String command) {
        outputStream.reset();
        errorStream.reset();
        StringBuilder exStr = new StringBuilder();

        try {
            ctx.handle(command);
        } catch (Exception e) {
            hasErrorBuffer = true;
            exStr.append(LINE_SEPARATOR).append(e.toString());
            for (StackTraceElement item : e.getStackTrace()) {
                exStr.append(LINE_SEPARATOR).append(item.toString());
            }
        }
        outBuffer = new String(outputStream.toByteArray(), StandardCharsets.UTF_8).trim();
        errBuffer = new String(errorStream.toByteArray(), StandardCharsets.UTF_8).trim();
        errBuffer += exStr.toString();
        if (!errBuffer.isEmpty()) {
            hasErrorBuffer = true;
        }
    }

    private void cwSendLine(String command) {
        errorStream.reset();
        StringBuilder exStr = new StringBuilder();

        try {
            cli.sendLineForValidation(command);
        } catch (Exception e) {
            hasErrorBuffer = true;
            exStr.append(LINE_SEPARATOR).append(e.toString());
            for (StackTraceElement item : e.getStackTrace()) {
                exStr.append(LINE_SEPARATOR).append(item.toString());
            }
        }
        outBuffer = cli.readOutput();
        errBuffer = new String(errorStream.toByteArray(), StandardCharsets.UTF_8).trim();
        errBuffer += exStr.toString();

        if (!errBuffer.isEmpty()) {
            hasErrorBuffer = true;
        }
    }

    public CommandContext getCtx() {
        return ctx;
    }
}
