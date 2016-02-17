/*
 *  Copyright 2014 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.cli;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.apache.commons.cli.*;
import org.teavm.tooling.RuntimeCopyOperation;
import org.teavm.tooling.TeaVMTool;
import org.teavm.tooling.TeaVMToolException;
import org.teavm.vm.TeaVMPhase;
import org.teavm.vm.TeaVMProgressFeedback;
import org.teavm.vm.TeaVMProgressListener;

/**
 *
 * @author Alexey Andreev
 */
public final class TeaVMRunner {
    private static long startTime;
    private static long phaseStartTime;
    private static TeaVMPhase currentPhase;
    private static String[] classPath;

    private TeaVMRunner() {
    }

    @SuppressWarnings("static-access")
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(OptionBuilder
                .withArgName("directory")
                .hasArg()
                .withDescription("a directory where to put generated files (current directory by default)")
                .withLongOpt("targetdir")
                .create('d'));
        options.addOption(OptionBuilder
                .withArgName("file")
                .hasArg()
                .withDescription("a file where to put decompiled classes (classes.js by default)")
                .withLongOpt("targetfile")
                .create('f'));
        options.addOption(OptionBuilder
                .withDescription("causes TeaVM to generate minimized JavaScript file")
                .withLongOpt("minify")
                .create("m"));
        options.addOption(OptionBuilder
                .withArgName("separate|merge|none")
                .hasArg()
                .withDescription("how to attach runtime. Possible values are: separate|merge|none")
                .withLongOpt("runtime")
                .create("r"));
        options.addOption(OptionBuilder
                .withDescription("causes TeaVM to include default main page")
                .withLongOpt("mainpage")
                .create());
        options.addOption(OptionBuilder
                .withDescription("causes TeaVM to log bytecode")
                .withLongOpt("logbytecode")
                .create());
        options.addOption(OptionBuilder
                .withDescription("Generate debug information")
                .withLongOpt("debug")
                .create('D'));
        options.addOption(OptionBuilder
                .withDescription("Generate source maps")
                .withLongOpt("sourcemaps")
                .create('S'));
        options.addOption(OptionBuilder
                .withDescription("Incremental build")
                .withLongOpt("incremental")
                .create('i'));
        options.addOption(OptionBuilder
                .withArgName("directory")
                .hasArg()
                .withDescription("Incremental build cache directory")
                .withLongOpt("cachedir")
                .create('c'));
        options.addOption(OptionBuilder
                .withDescription("Wait for command after compilation, in order to enable hot recompilation")
                .withLongOpt("wait")
                .create('w'));
        options.addOption(OptionBuilder
                .withArgName("classpath")
                .hasArgs()
                .withDescription("Additional classpath that will be reloaded by TeaVM each time in wait mode")
                .withLongOpt("classpath")
                .create('p'));

        if (args.length == 0) {
            printUsage(options);
            return;
        }
        CommandLineParser parser = new PosixParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            printUsage(options);
            return;
        }

        TeaVMTool tool = new TeaVMTool();
        tool.setBytecodeLogging(commandLine.hasOption("logbytecode"));
        if (commandLine.hasOption("d")) {
            tool.setTargetDirectory(new File(commandLine.getOptionValue("d")));
        }
        if (commandLine.hasOption("f")) {
            tool.setTargetFileName(commandLine.getOptionValue("f"));
        }
        if (commandLine.hasOption("m")) {
            tool.setMinifying(true);
        } else {
            tool.setMinifying(false);
        }
        if (commandLine.hasOption("r")) {
            switch (commandLine.getOptionValue("r")) {
                case "separate":
                    tool.setRuntime(RuntimeCopyOperation.SEPARATE);
                    break;
                case "merge":
                    tool.setRuntime(RuntimeCopyOperation.MERGED);
                    break;
                case "none":
                    tool.setRuntime(RuntimeCopyOperation.NONE);
                    break;
                default:
                    System.err.println("Wrong parameter for -r option specified");
                    printUsage(options);
                    return;
            }
        }
        if (commandLine.hasOption("mainpage")) {
            tool.setMainPageIncluded(true);
        }
        if (commandLine.hasOption('D')) {
            tool.setDebugInformationGenerated(true);
        }
        if (commandLine.hasOption('S')) {
            tool.setSourceMapsFileGenerated(true);
        }
        if (commandLine.hasOption('i')) {
            tool.setIncremental(true);
        }
        if (commandLine.hasOption('c')) {
            tool.setCacheDirectory(new File(commandLine.getOptionValue('c')));
        } else {
            tool.setCacheDirectory(new File(tool.getTargetDirectory(), "teavm-cache"));
        }
        if (commandLine.hasOption('p')) {
            classPath = commandLine.getOptionValues('p');
        }
        boolean interactive = commandLine.hasOption('w');
        args = commandLine.getArgs();
        if (args.length > 1) {
            System.err.println("Unexpected arguments");
            printUsage(options);
            return;
        } else if (args.length == 1) {
            tool.setMainClass(args[0]);
        }
        tool.setLog(new ConsoleTeaVMToolLog());
        tool.getProperties().putAll(System.getProperties());
        tool.setProgressListener(progressListener);

        if (interactive) {
            boolean quit = false;
            BufferedReader reader;
            try {
                reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                System.exit(-2);
                return;
            }
            do {
                try {
                    build(tool);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
                System.out.println("Press enter to repeat or enter 'q' to quit");
                try {
                    String line = reader.readLine().trim();
                    if (!line.isEmpty()) {
                        if (line.equals("q")) {
                            quit = true;
                        } else {
                            System.out.println("Unrecognized command");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-2);
                }
            } while (!quit);
        } else {
            try {
                build(tool);
            } catch (Exception e) {
                e.printStackTrace(System.err);
                System.exit(-2);
            }
            if (!tool.getProblemProvider().getSevereProblems().isEmpty()) {
                System.exit(-2);
            }
        }
    }

    private static void build(TeaVMTool tool) throws TeaVMToolException {
        resetClassLoader(tool);
        currentPhase = null;
        startTime = System.currentTimeMillis();
        phaseStartTime = System.currentTimeMillis();
        tool.generate();
        reportPhaseComplete();
        System.out.println("Build complete for " + ((System.currentTimeMillis() - startTime) / 1000.0) + " seconds");
    }

    private static void resetClassLoader(TeaVMTool tool) {
        if (classPath == null || classPath.length == 0) {
            return;
        }
        URL[] urls = new URL[classPath.length];
        for (int i = 0; i < classPath.length; ++i) {
            try {
                urls[i] = new File(classPath[i]).toURI().toURL();
            } catch (MalformedURLException e) {
                System.err.println("Illegal classpath entry: " + classPath[i]);
                System.exit(-1);
                return;
            }
        }

        tool.setClassLoader(new URLClassLoader(urls, TeaVMRunner.class.getClassLoader()));
    }

    private static TeaVMProgressListener progressListener = new TeaVMProgressListener() {
        @Override
        public TeaVMProgressFeedback progressReached(int progress) {
            return TeaVMProgressFeedback.CONTINUE;
        }
        @Override
        public TeaVMProgressFeedback phaseStarted(TeaVMPhase phase, int count) {
            if (currentPhase != phase) {
                if (currentPhase != null) {
                    reportPhaseComplete();
                }
                phaseStartTime = System.currentTimeMillis();
                switch (phase) {
                    case DEPENDENCY_CHECKING:
                        System.out.print("Finding methods to decompile...");
                        break;
                    case LINKING:
                        System.out.print("Linking methods...");
                        break;
                    case DEVIRTUALIZATION:
                        System.out.print("Applying devirtualization...");
                        break;
                    case DECOMPILATION:
                        System.out.print("Decompiling...");
                        break;
                    case RENDERING:
                        System.out.print("Generating output...");
                        break;
                }
                currentPhase = phase;
            }
            return TeaVMProgressFeedback.CONTINUE;
        }
    };

    private static void reportPhaseComplete() {
        System.out.println(" complete for " + ((System.currentTimeMillis() - phaseStartTime) / 1000.0) + " seconds");
    }

    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java " + TeaVMRunner.class.getName() + " [OPTIONS] [qualified.main.Class]", options);
        System.exit(-1);
    }
}
