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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.testing.TestAdapter;
import org.teavm.tooling.TeaVMToolException;
import org.teavm.tooling.testing.TeaVMTestTool;

/**
 *
 * @author Alexey Andreev
 */
public final class TeaVMTestRunner {
    private TeaVMTestRunner() {
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
                .withDescription("causes TeaVM to generate minimized JavaScript file")
                .withLongOpt("minify")
                .create("m"));
        options.addOption(OptionBuilder
                .withArgName("number")
                .hasArg()
                .withDescription("how many threads should TeaVM run")
                .withLongOpt("threads")
                .create("t"));
        options.addOption(OptionBuilder
                .withArgName("class name")
                .hasArg()
                .withDescription("qualified class name of test adapter")
                .withLongOpt("adapter")
                .create("a"));
        options.addOption(OptionBuilder
                .hasArg()
                .hasOptionalArgs()
                .withArgName("class name")
                .withDescription("qualified class names of transformers")
                .withLongOpt("transformers")
                .create("T"));
        options.addOption(OptionBuilder
                .withDescription("Incremental build")
                .withLongOpt("incremental")
                .create('i'));

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

        TeaVMTestTool tool = new TeaVMTestTool();
        tool.setTargetDirectory(new File(commandLine.getOptionValue("d", ".")));
        tool.setMinifying(commandLine.hasOption("m"));
        try {
            tool.setNumThreads(Integer.parseInt(commandLine.getOptionValue("t", "1")));
        } catch (NumberFormatException e) {
            System.err.println("Invalid number specified for -t option");
            printUsage(options);
            return;
        }
        if (commandLine.hasOption("a")) {
            tool.setAdapter(instantiateAdapter(commandLine.getOptionValue("a")));
        }
        if (commandLine.hasOption("T")) {
            for (String transformerType : commandLine.getOptionValues("T")) {
                tool.getTransformers().add(instantiateTransformer(transformerType));
            }
        }
        if (commandLine.hasOption('i')) {
            tool.setIncremental(true);
        }

        args = commandLine.getArgs();
        if (args.length == 0) {
            System.err.println("You did not specify any test classes");
            printUsage(options);
            return;
        }
        tool.getTestClasses().addAll(Arrays.asList(args));

        tool.setLog(new ConsoleTeaVMToolLog());
        tool.getProperties().putAll(System.getProperties());
        long start = System.currentTimeMillis();
        try {
            tool.generate();
        } catch (TeaVMToolException e) {
            e.printStackTrace(System.err);
            System.exit(-2);
        }
        System.out.println("Operation took " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    private static TestAdapter instantiateAdapter(String adapterName) {
        Class<?> adapterClass;
        try {
            adapterClass = Class.forName(adapterName, true, TeaVMTestRunner.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            System.err.println("Adapter not found: " + adapterName);
            System.exit(-1);
            return null;
        }
        if (!TestAdapter.class.isAssignableFrom(adapterClass)) {
            System.err.println("Adapter class does not implement TestAdapter: " + adapterName);
            System.exit(-1);
            return null;
        }
        Constructor<?> cons;
        try {
            cons = adapterClass.getConstructor();
        } catch (NoSuchMethodException e) {
            System.err.println("Adapter class does not contain no-arg constructor: " + adapterName);
            System.exit(-1);
            return null;
        }

        try {
            return (TestAdapter) cons.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            System.err.println("Error instantiating adapter: " + adapterName);
            e.printStackTrace(System.err);
            System.exit(-1);
            return null;
        } catch (InvocationTargetException e) {
            System.err.println("Error instantiating adapter: " + adapterName);
            e.getTargetException().printStackTrace(System.err);
            System.exit(-1);
            return null;
        }
    }

    private static ClassHolderTransformer instantiateTransformer(String transformerName) {
        Class<?> adapterClass;
        try {
            adapterClass = Class.forName(transformerName, true, TeaVMTestRunner.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            System.err.println("Transformer not found: " + transformerName);
            System.exit(-1);
            return null;
        }
        if (!ClassHolderTransformer.class.isAssignableFrom(adapterClass)) {
            System.err.println("Transformer class does not implement ClassHolderTransformer: " + transformerName);
            System.exit(-1);
            return null;
        }
        Constructor<?> cons;
        try {
            cons = adapterClass.getConstructor();
        } catch (NoSuchMethodException e) {
            System.err.println("Transformer class does not contain no-arg constructor: " + transformerName);
            System.exit(-1);
            return null;
        }

        try {
            return (ClassHolderTransformer) cons.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            System.err.println("Error instantiating transformer: " + transformerName);
            e.printStackTrace(System.err);
            System.exit(-1);
            return null;
        } catch (InvocationTargetException e) {
            System.err.println("Error instantiating transformer: " + transformerName);
            e.getTargetException().printStackTrace(System.err);
            System.exit(-1);
            return null;
        }
    }

    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java " + TeaVMTestRunner.class.getName() + " [OPTIONS] test_name {test_name}", options);
        System.exit(-1);
    }
}
