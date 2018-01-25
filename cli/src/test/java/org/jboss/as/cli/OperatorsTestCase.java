/*
Copyright 2017 Red Hat, Inc.

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
package org.jboss.as.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class OperatorsTestCase {

    @Test
    public void testPipe() throws CliInitializationException, CommandLineException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        try {
            ctx.handle("version | grep Rel");
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testRedirectOut() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File f = File.createTempFile("cli_test" + System.currentTimeMillis(), null);
        f.delete();
        try {
            ctx.handle("version > " + f.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(f.toPath());
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content " + lines);
            }
            consoleOutput.reset();
            ctx.handle("grep Rel " + f.getAbsolutePath());
            out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            f.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testPipeRedirectOut() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File f = File.createTempFile("cli_test" + System.currentTimeMillis(), null);
        f.delete();
        try {
            ctx.handle("version | grep Rel > " + f.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(f.toPath());
            Assert.assertTrue(lines.toString(), lines.size() == 1);
            Assert.assertTrue(lines.toString(), lines.get(0).contains("Release:"));
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content " + lines);
            }
        } finally {
            f.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testAppendOut() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File f = File.createTempFile("cli_test" + System.currentTimeMillis(), null);
        try {
            ctx.handle("version >> " + f.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(f.toPath());
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content in " + f + ": " + lines);
            }
            consoleOutput.reset();
            ctx.handle("grep Rel " + f.getAbsolutePath());
            out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            f.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testVariables() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        ctx.handle("set varName=Rel");
        try {
            ctx.handle("version | grep $varName");
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testErrors() throws Exception {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        try {
            ctx.handle("version ||");
            throw new Exception("Should have failed");
        } catch (CommandLineException ex) {
            Assert.assertTrue(ex.toString(), ex.getMessage().contains("Failed to handle 'version ||'"));
        } finally {
            ctx.terminateSession();
        }
    }
}
