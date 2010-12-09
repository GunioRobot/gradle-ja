/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.wrapper;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.wrapper.internal.WrapperScriptGenerator;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.net.URL;
import java.util.Properties;

/**
 * <p>Generates scripts (for *nix and windows) which allow you to build your project with Gradle, without having to
 * install Gradle. The scripts generated by this task are intended to be committed to your version control system. When
 * a user executes a wrapper script the first time, the script downloads and installs the appropriate Gradle
 * distribution and runs the build against this downloaded distribution.
 *
 * <p>This task also generates a small {@code gradle-wrapper.jar} bootstrap JAR file which should also be committed to
 * your VCS. The scripts delegates to this jar. Any installed Gradle distribution is ignored when using the wrapper
 * scripts
 *
 * @author Hans Dockter
 */
public class Wrapper extends DefaultTask {
    // Properties used by the gradle-wrapper
    static final String URL_ROOT_PROPERTY = "urlRoot";
    static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";
    static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";
    static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";
    static final String DISTRIBUTION_VERSION_PROPERTY = "distributionVersion";
    static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";
    static final String DISTRIBUTION_NAME_PROPERTY = "distributionName";
    static final String DISTRIBUTION_CLASSIFIER_PROPERTY = "distributionClassifier";
    static final String WRAPPER_DIR = "gradle-wrapper";
    static final String WRAPPER_JAR = WRAPPER_DIR + ".jar";
    static final String WRAPPER_PROPERTIES = WRAPPER_DIR + ".properties";

    public static final String DEFAULT_URL_ROOT = "http://dist.codehaus.org/gradle";
    public static final String WRAPPER_JAR_BASE_NAME = "gradle-wrapper";
    public static final String DEFAULT_DISTRIBUTION_PARENT_NAME = "wrapper/dists";
    public static final String DEFAULT_ARCHIVE_NAME = "gradle";
    public static final String DEFAULT_ARCHIVE_CLASSIFIER = "bin";

    /**
     * Specifies how the wrapper path should be interpreted.
     */
    public enum PathBase {
        PROJECT, GRADLE_USER_HOME
    }

    @Input
    private String scriptDestinationPath;

    @Input
    private String jarPath;

    @Input
    private String distributionPath;

    @Input
    private String archiveName;

    @Input
    private String archiveClassifier;

    @Input
    private PathBase distributionBase = PathBase.GRADLE_USER_HOME;

    @Input
    private String gradleVersion;

    @Input
    private String urlRoot;

    @Input
    private String archivePath;

    @Input
    private PathBase archiveBase = PathBase.GRADLE_USER_HOME;

    private WrapperScriptGenerator wrapperScriptGenerator = new WrapperScriptGenerator();

    public Wrapper() {
        scriptDestinationPath = "";
        jarPath = "";
        distributionPath = DEFAULT_DISTRIBUTION_PARENT_NAME;
        archiveName = DEFAULT_ARCHIVE_NAME;
        archiveClassifier = DEFAULT_ARCHIVE_CLASSIFIER;
        archivePath = DEFAULT_DISTRIBUTION_PARENT_NAME;
        urlRoot = DEFAULT_URL_ROOT;
    }

    @TaskAction
    void generate() {
        String wrapperDir = GUtil.isTrue(jarPath) ? jarPath + "/" : "";
        new File(getProject().getProjectDir(), wrapperDir).mkdirs();
        String wrapperJar = wrapperDir + WRAPPER_JAR;
        String wrapperPropertiesPath = wrapperDir + WRAPPER_PROPERTIES;
        File jarFileDestination = new File(getProject().getProjectDir(), wrapperJar);
        File propertiesFileDestination = new File(getProject().getProjectDir(), wrapperPropertiesPath);
        URL jarFileSource = getClass().getResource("/" + WRAPPER_JAR_BASE_NAME + ".jar");
        if (jarFileSource == null) {
            throw new GradleException("Cannot locate wrapper JAR resource.");
        }
        propertiesFileDestination.delete();
        jarFileDestination.delete();
        writeProperties(propertiesFileDestination);
        GFileUtils.copyURLToFile(jarFileSource, jarFileDestination);
        File scriptDestinationDir = new File(getProject().getProjectDir(), scriptDestinationPath);
        wrapperScriptGenerator.generate(wrapperJar, wrapperPropertiesPath, scriptDestinationDir);
    }

    private void writeProperties(File propertiesFileDestination) {
        Properties wrapperProperties = new Properties();
        wrapperProperties.put(URL_ROOT_PROPERTY, urlRoot);
        wrapperProperties.put(DISTRIBUTION_BASE_PROPERTY, distributionBase.toString());
        wrapperProperties.put(DISTRIBUTION_PATH_PROPERTY, distributionPath);
        wrapperProperties.put(DISTRIBUTION_NAME_PROPERTY, archiveName);
        wrapperProperties.put(DISTRIBUTION_CLASSIFIER_PROPERTY, archiveClassifier);
        wrapperProperties.put(DISTRIBUTION_VERSION_PROPERTY, gradleVersion);
        wrapperProperties.put(ZIP_STORE_BASE_PROPERTY, archiveBase.toString());
        wrapperProperties.put(ZIP_STORE_PATH_PROPERTY, archivePath);
        GUtil.saveProperties(wrapperProperties, propertiesFileDestination);
    }

    /**
     * Returns the script destination path.
     *
     * @see #setScriptDestinationPath(String)
     */
    public String getScriptDestinationPath() {
        return scriptDestinationPath;
    }

    /**
     * Specifies a path as the parent dir of the scripts which are generated when executing the wrapper task. This path
     * specifies a directory <i>relative</i> to the project dir.  Defaults to empty string, i.e. the scripts are placed
     * into the project root dir.
     *
     * @param scriptDestinationPath Any object which <code>toString</code> method specifies the path. Most likely a
     * String or File object.
     */
    public void setScriptDestinationPath(String scriptDestinationPath) {
        this.scriptDestinationPath = scriptDestinationPath;
    }

    /**
     * Returns the jar path.
     *
     * @see #setJarPath(String)
     */
    public String getJarPath() {
        return jarPath.toString();
    }

    /**
     * When executing the wrapper task, the jar path specifies the path where the gradle-wrapper.jar is copied to. The
     * jar path must be a path relative to the project dir. The gradle-wrapper.jar must be submitted to your version
     * control system. Defaults to empty string, i.e. the jar is placed into the project root dir.
     */
    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    /**
     * Returns the distribution path.
     *
     * @see #setDistributionPath(String)
     */
    public String getDistributionPath() {
        return distributionPath;
    }

    /**
     * Set's the path where the gradle distributions needed by the wrapper are unzipped. The path is relative to the dir
     * specified with {@link #distributionBase}.
     */
    public void setDistributionPath(String distributionPath) {
        this.distributionPath = distributionPath;
    }

    /**
     * Returns the gradle version for the wrapper.
     *
     * @see #setGradleVersion(String)
     */
    public String getGradleVersion() {
        return gradleVersion;
    }

    /**
     * The version of the gradle distribution required by the wrapper. This is usually the same version of Gradle you
     * use for building your project.
     */
    public void setGradleVersion(String gradleVersion) {
        this.gradleVersion = gradleVersion;
    }

    public String getUrlRoot() {
        return urlRoot;
    }

    /**
     * A URL where to download the gradle distribution from. The pattern used by the wrapper for downloading is:
     * <code>{@link #getUrlRoot()}/{@link #getArchiveName()}-{@link #getArchiveClassifier()}-{@link
     * #getGradleVersion()}.zip</code>. The default for the URL root is {@link #DEFAULT_URL_ROOT}.
     *
     * The wrapper downloads a certain distribution only ones and caches it. If your {@link #getDistributionBase()} is
     * the project, you might submit the distribution to your version control system. That way no download is necessary
     * at all. This might be in particular interesting, if you provide a custom gradle snapshot to the wrapper, because
     * you don't need to provide a download server then.
     */
    public void setUrlRoot(String urlRoot) {
        this.urlRoot = urlRoot;
    }

    /**
     * Returns the distribution base.
     *
     * @see #setDistributionBase(org.gradle.api.tasks.wrapper.Wrapper.PathBase)
     */
    public PathBase getDistributionBase() {
        return distributionBase;
    }

    /**
     * The distribution base specifies whether the unpacked wrapper distribution should be stored in the project or in
     * the gradle user home dir. The path specified in {@link #distributionPath} is a relative path to either of those
     * dirs.
     */
    public void setDistributionBase(PathBase distributionBase) {
        this.distributionBase = distributionBase;
    }

    /**
     * Returns the archive path.
     *
     * @see #setArchivePath(String)
     */
    public String getArchivePath() {
        return archivePath;
    }

    /**
     * Set's the path where the gradle distributions archive should be saved (i.e. the parent dir). The path is relative
     * to the parent dir specified with {@link #getArchiveBase()}.
     */
    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    /**
     * Returns the archive base.
     *
     * @see #setArchiveBase(org.gradle.api.tasks.wrapper.Wrapper.PathBase)
     */
    public PathBase getArchiveBase() {
        return archiveBase;
    }

    /**
     * The distribution base specifies whether the unpacked wrapper distribution should be stored in the project or in
     * the gradle user home dir. The path specified in {@link #getArchivePath()} is a relative path to etiher of those
     * dirs.
     */
    public void setArchiveBase(PathBase archiveBase) {
        this.archiveBase = archiveBase;
    }

    /**
     * Returnes the archive name.
     *
     * @see #setArchiveName(String)
     */
    public String getArchiveName() {
        return archiveName;
    }

    /**
     * Specifies the name of the archive as part of the download URL. The download URL is assembled by the pattern:
     *
     * <code>{@link #getUrlRoot()}/{@link #getArchiveName()}-{@link #getArchiveClassifier()}-{@link
     * #getGradleVersion()}.zip</code>
     *
     * The default for the archive name is {@link #DEFAULT_ARCHIVE_NAME}.
     */
    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
    }

    /**
     * Returns the archive classifier.
     *
     * @see #setArchiveClassifier(String)
     */
    public String getArchiveClassifier() {
        return archiveClassifier;
    }

    /**
     * Specifies the classifier of the archive as part of the download URL. The download URL is assembled by the
     * pattern:
     *
     * <code>{@link #getUrlRoot()}/{@link #getArchiveName()}-{@link #getArchiveClassifier()}-{@link
     * #getGradleVersion()}.zip</code>
     *
     * The default for the archive classifier is {@link #DEFAULT_ARCHIVE_CLASSIFIER}.
     */
    public void setArchiveClassifier(String archiveClassifier) {
        this.archiveClassifier = archiveClassifier;
    }

    public WrapperScriptGenerator getUnixWrapperScriptGenerator() {
        return wrapperScriptGenerator;
    }

    public void setUnixWrapperScriptGenerator(WrapperScriptGenerator wrapperScriptGenerator) {
        this.wrapperScriptGenerator = wrapperScriptGenerator;
    }
}
