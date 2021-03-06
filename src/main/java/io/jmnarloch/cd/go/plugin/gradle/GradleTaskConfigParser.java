/**
 * Copyright (c) 2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jmnarloch.cd.go.plugin.gradle;

import com.thoughtworks.go.plugin.api.logging.Logger;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionConfiguration;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.jmnarloch.cd.go.plugin.gradle.Gradle.gradle;
import static io.jmnarloch.cd.go.plugin.gradle.Gradle.gradlew;

/**
 * The Gradle configuration parser, that converts the flags into form of command line options needed to execute the
 * Gradle task. Provides the mapping between the task configuration and the actual Gradle process command line options.
 *
 * @author Jakub Narloch
 */
class GradleTaskConfigParser {

    /**
     * The logger instance used by this class.
     */
    private static final Logger logger = Logger.getLoggerFor(GradleTaskConfigParser.class);

    /**
     * The Gradle home. Needed when the wrapper is not used and the Gradle is not specified on system Path.
     */
    private static final String GRADLE_HOME = "GRADLE_HOME";

    /**
     * Path to the Gradle executables within the GRADLE_HOME.
     */
    private static final String GRADLE_BIN = "bin";

    /**
     * The PATH environment variable.
     */
    private static final String PATH = "PATH";

    /**
     * The operating system property name.
     */
    private static final String OS_NAME = "os.name";

    /**
     * The task configuration.
     */
    private final ExecutionConfiguration configuration;

    /**
     * The Gradle tasks.
     */
    private final List<String> tasks = new ArrayList<String>();

    /**
     * The Gradle options.
     */
    private final List<String> options = new ArrayList<String>();

    /**
     * The working directory.
     */
    private String workingDirectory;

    /**
     * The execution environment.
     */
    private Map<String, String> environment = new HashMap<String, String>();

    /**
     * Whether to use Gradle wrapper.
     */
    private boolean useWrapper;

    /**
     * Whether to make Gradle wrapper executable.
     */
    private boolean makeWrapperExecutable;

    /**
     * Gradle HOME dir.
     */
    private String gradleHome;

    /**
     * Creates new instance of {@link GradleTaskConfigParser}.
     *
     * @param config the task configuration
     */
    private GradleTaskConfigParser(ExecutionConfiguration config) {
        this.configuration = config;
    }

    /**
     * Specifies the build environment.
     *
     * @param environment the environment
     * @return the config parser
     */
    GradleTaskConfigParser withEnvironment(Map<String, String> environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Specifies the working directory.
     *
     * @param workingDirectory the working directory
     * @return the config parser
     */
    GradleTaskConfigParser withWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    /**
     * Specifies whether to use the Gradle wrapper.
     *
     * @param propertyKey the name of the property that specifies this setting
     * @return the config parser
     */
    GradleTaskConfigParser useWrapper(String propertyKey) {
        useWrapper = isPropertySet(propertyKey);
        return this;
    }

    /**
     * Specifies whether to make the Gradle wrapper script executable.
     *
     * @param propertyKey the name of the property that specifies this setting
     * @return the config parser
     */
    GradleTaskConfigParser makeWrapperExecutable(String propertyKey) {
        makeWrapperExecutable = isPropertySet(propertyKey);
        return this;
    }

    /**
     * Specifies the Gradle home directory.
     *
     * @param propertyKey the name of the property that specifies this setting
     * @return the config parser
     */
    GradleTaskConfigParser withGradleHome(String propertyKey) {
        gradleHome = configuration.getProperty(propertyKey);
        return this;
    }

    /**
     * Specifies the Gradle build file tasks to be executed.
     *
     * @param propertyKey the name of the property that specifies this setting
     * @return the config parser
     */
    GradleTaskConfigParser withTasks(String propertyKey) {
        final String tasks = configuration.getProperty(propertyKey);
        if (!StringUtils.isBlank(tasks)) {
            this.tasks.addAll(Arrays.asList(tasks.split("\\s+")));
        }
        return this;
    }

    /**
     * Registers command line option.
     *
     * @param propertyKey the name of the property that specifies this setting
     * @param option      the corresponding Gradle command line option
     * @return the config parser
     */
    GradleTaskConfigParser withOption(String propertyKey, String option) {
        if (isPropertySet(propertyKey)) {
            options.add(option);
        }
        return this;
    }

    /**
     * Specifies the additional Gradle command line options to be passed to the build.
     *
     * @param propertyKey the name of the property that specifies this setting
     * @return the config parser
     */
    GradleTaskConfigParser withAdditionalOptions(String propertyKey) {
        final String additional = configuration.getProperty(propertyKey);
        if (!StringUtils.isBlank(additional)) {
            options.addAll(Arrays.asList(additional.split("\\s+")));
        }
        return this;
    }

    /**
     * Builds the command line process.
     *
     * @return the list of task to be executed
     */
    List<String> build() {
        final List<String> command = new ArrayList<String>();

        if (useWrapper) {
            setGradlewCommand(command);
        } else {
            setGradleCommand(command);
        }
        command.addAll(options);
        command.addAll(tasks);
        return command;
    }

    /**
     * Creates new instance of {@link GradleTaskConfigParser}.
     *
     * @param config the task configuration
     * @return the config parser
     */
    public static GradleTaskConfigParser fromConfig(ExecutionConfiguration config) {
        return new GradleTaskConfigParser(config);
    }

    /**
     * Specifies the Gradle command to be executed on system.
     *
     * @param command the list of commands
     */
    private void setGradleCommand(List<String> command) {
        final String gradleHome = getGradleHome();

        String gradle;
        if (isWindows()) {
            gradle = gradle().windows();
        } else {
            gradle = gradle().unix();
        }

        String gradleCommand = gradle;
        if (!StringUtils.isBlank(gradleHome)) {
            gradleCommand = Paths.get(gradleHome, GRADLE_BIN, gradle).toAbsolutePath().normalize().toString();
        } else {
            gradleCommand = getExecutablePath(gradleCommand);
        }
        command.add(gradleCommand);
    }


    /**
     * Sets the Gradlew command to be executed on system.
     *
     * @param command the command lists
     */
    private void setGradlewCommand(List<String> command) {
        String gradleCommand;
        if (isWindows()) {
            gradleCommand = gradlew().windows();
        } else {
            gradleCommand = gradlew().unix();
        }

        final String gradlewPath = Paths.get(workingDirectory, gradleCommand).toAbsolutePath().normalize().toString();
        command.add(gradlewPath);
        if (!isWindows() && makeWrapperExecutable) {
            addExecutablePermission(gradlewPath);
        }
    }

    /**
     * Adds the executable file permission.
     *
     * @param file the path to the file
     */
    private void addExecutablePermission(String file) {
        final Path path = Paths.get(file);
        if (Files.exists(path)) {
            try {
                PosixFileAttributeView attr = Files.getFileAttributeView(path, PosixFileAttributeView.class);
                Set<PosixFilePermission> permissions = attr.readAttributes().permissions();
                if(permissions.add(PosixFilePermission.OWNER_EXECUTE)) {
                    logger.info(String.format("Added +x permission to file: %s", file));
                }
                attr.setPermissions(permissions);
            } catch (IOException e) {
                logger.error(String.format("Failed to add the executable permissions to file: %s", file));
            }
        }
    }

    /**
     * Retrieves the Gradle home directory, which might be either specified as environment variable or overridden for
     * specific task.
     *
     * @return the Gradle home
     */
    private String getGradleHome() {
        if (!StringUtils.isBlank(gradleHome)) {
            return gradleHome;
        } else if (!StringUtils.isBlank(environment.get(GRADLE_HOME))) {
            return environment.get(GRADLE_HOME);
        }
        return null;
    }

    /**
     * Finds first matching path to executable file by iterating over all system path entries.
     *
     * @param command the command
     * @return the absolute path to the executable file
     */
    private String getExecutablePath(String command) {
        final String systemPath = getEnvironmentVariable(PATH);
        if (StringUtils.isBlank(systemPath)) {
            return command;
        }
        final String[] paths = systemPath.split(File.pathSeparator);
        for (String path : paths) {
            if (Files.exists(Paths.get(path, command))) {
                return Paths.get(path, command).toAbsolutePath().normalize().toString();
            }
        }
        return command;
    }

    /**
     * Returns whether the given property has been set or not.
     *
     * @param propertyKey the property name
     * @return true if the property value has been specified, false otherwise
     */
    private boolean isPropertySet(String propertyKey) {
        return isSet(configuration.getProperty(propertyKey));
    }

    /**
     * Returns whether the given string property is present.
     *
     * @param value the property value
     * @return true if the property value has been specified, false otherwise
     */
    private static boolean isSet(String value) {
        return !StringUtils.isBlank(value) && Boolean.valueOf(value);
    }

    /**
     * Returns whether current OS family is Windows.
     *
     * @return true if the current task is executed on Windows
     */
    private boolean isWindows() {
        final String os = environment.containsKey(OS_NAME) ? environment.get(OS_NAME) : System.getProperty(OS_NAME);
        return !StringUtils.isBlank(os) && os.toLowerCase().contains("win");
    }

    /**
     * Retrieves the environment variable.
     *
     * @param property the property name
     * @return the environment variable
     */
    private String getEnvironmentVariable(String property) {

        return environment.containsKey(property) ? environment.get(property) : System.getenv(property);
    }
}
