/*
 * Copyright 2014-2017 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twcable.gradle.cqpackage;

import lombok.val;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.jvm.tasks.Jar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.ALL;
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.NONE;
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.NON_PROJECT_ONLY;
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.PROJECT_ONLY;

@SuppressWarnings({"Convert2MethodRef", "WeakerAccess"})
public class CreatePackageTask extends Zip {
    private static final Logger LOG = LoggerFactory.getLogger(CreatePackageTask.class);

    private String _bundleInstallRoot = "/apps/install";
    private File _contentSrc;
    private Configuration _configuration;
    private @MonotonicNonNull String customName;


    /**
     * Mutable collection of exclusions to apply to copying files from {@link #getContentSrc()}.
     * Defaults with a "reasonable" set, such as "*&#42;/.vlt", "*&#42;/.git/**", etc.
     * Generally to modify this list you would mutate this in-place.
     */
    private Collection<String> fileExclusions;

    private CopyBundlesMode copyBundlesMode = ALL;

    public enum CopyBundlesMode {
        ALL, PROJECT_ONLY, NON_PROJECT_ONLY, NONE
    }


    public CreatePackageTask() {
        super();

        // AbstractTask() guarantees that this.project has been set

        getProject().getTasks().withType(Jar.class, it -> dependsOn(it));

        setDefaults();
        addVaultFilter();

        addValidator((taskInternal, errorMessages) -> {
            // Resolves https://github.com/TWCable/gradle-plugin-cq-package/issues/3
            if (getDestinationDir() == null) errorMessages.add("Missing destinationDirectory");
            if (getBaseName() == null && customName == null) errorMessages.add("Missing baseName and archiveName");
        });

        getProject().afterEvaluate(it -> {
            addVaultDefinition(getContentSrc());
            addBundles(getBundleInstallRoot());
            addExclusions(fileExclusions);

            // Resolves https://github.com/TWCable/gradle-plugin-cq-package/issues/2
            dependsOn(getConfiguration());
        });
    }


    public void setArchiveName(String name) {
        this.customName = name; // capture for validation purposes
        super.setArchiveName(name);
    }


    private void setDefaults() {
        setDescription("Creates the CQ Package zip file");

        getProject().getTasks().
            matching(it -> it instanceof VerifyBundlesTask || it instanceof AddBundlesToFilterXmlTask).
            all(it -> this.dependsOn(it));

        fileExclusions = new ArrayList<>(Arrays.asList("**/.git", "**/.git/**", "**/.gitattributes",
            "**/.gitignore", "**/.gitmodules", "**/.vlt", "jcr_root/.vlt-sync-config.properties", "jcr_root/var/**",
            "SLING-INF/**"));

        setContentSrc(getProject().file("src/main/content"));
    }


    // TODO remove
    public static CreatePackageTask from(final Project project) {
        if (project == null) throw new IllegalArgumentException("project == null");
        val tasks = project.getTasks().withType(CreatePackageTask.class);
        if (tasks == null || tasks.isEmpty()) {
            LOG.warn("Creating a \"createPackage\" task automatically. You should either do this " +
                "explicitly with the {} class, or by applying " +
                "the \"{}\" plugin", CreatePackageTask.class.getName(), CqPackagePlugin.NAME);
            return project.getTasks().create("createPackage", CreatePackageTask.class);
        }

        if (tasks.size() > 1)
            throw new IllegalArgumentException(project + " has more than one " + CreatePackageTask.class.getName());
        return tasks.iterator().next();
    }


    /**
     * All the bundles that this depends on (project and non-project) will be copied into the "install" folder.
     * This is the default behavior.
     *
     * @see #addProjectBundles()
     * @see #addNonProjectBundles()
     * @see #addNoBundles()
     */
    public void addAllBundles() {
        copyBundlesMode = ALL;
    }


    /**
     * Only the project-generated bundles that this depends on will be copied into the "install" folder.
     *
     * @see #addAllBundles()
     * @see #addProjectBundles()
     * @see #addNonProjectBundles()
     * @see #addNoBundles()
     */
    public void addProjectBundles() {
        copyBundlesMode = PROJECT_ONLY;
    }


    /**
     * Only the non-project generated bundles that this depends on be will copied into the "install" folder.
     *
     * @see #addAllBundles()
     * @see #addProjectBundles()
     * @see #addNoBundles()
     */
    public void addNonProjectBundles() {
        copyBundlesMode = NON_PROJECT_ONLY;
    }


    /**
     * None of the bundles that this depends on will be copied into the "install" folder.
     *
     * @see #addAllBundles()
     * @see #addProjectBundles()
     * @see #addNonProjectBundles()
     */
    public void addNoBundles() {
        copyBundlesMode = NONE;
    }


    /**
     * The root of the content tree for files that it will copy into the package.
     * Defaults to `project.file("src/main/content")`
     */
    public void setContentSrc(File contentSrc) {
        val sourcePaths = ((DefaultCopySpec)getMainSpec()).getSourcePaths();

        if (_contentSrc != null) sourcePaths.remove(this._contentSrc);

        sourcePaths.add(contentSrc);

        this._contentSrc = contentSrc;
    }


    /**
     * The root of the content tree for files that it will copy into the package.
     * Defaults to `project.file("src/main/content")`
     */
    @InputDirectory
    public File getContentSrc() {
        return _contentSrc;
    }


    @Input
    @SuppressWarnings("unused") // lets Gradle know when to not skip the task
    public String getConfigurationName() {
        return getConfiguration().getName();
    }


    @SuppressWarnings("unused")
    public void setConfigurationName(String confName) {
        setConfiguration(getProject().getConfigurations().getByName(confName));
    }


    /**
     * The Configuration to use when determining what bundles to put in the package.
     *
     * @see #addAllBundles()
     */
    @Internal
    public Configuration getConfiguration() {
        if (_configuration == null) _configuration = cqPackageConfiguration(getProject());

        return _configuration;
    }


    public void setConfiguration(Configuration conf) {
        this._configuration = conf;
    }


    @InputFiles
    @SuppressWarnings("unused") // lets Gradle know when to not skip the task
    public FileCollection getDependencyFiles() {
        return getProject().files(getConfiguration().getResolvedConfiguration().getFiles());
    }


    protected void addExclusions(Collection<String> fileExclusions) {
        fileExclusions.forEach(it -> exclude(it));
    }


    protected void addVaultDefinition(File contentSrc) {
        this.exclude("META-INF/vault/definition/.content.xml");
        this.exclude("META-INF/vault/properties.xml");

        this.into("META-INF/vault", it -> {
            it.from(new File(contentSrc, "META-INF/vault/properties.xml"));
            it.expand(getProject().getProperties());
        });

        this.into("META-INF/vault/definition", it -> {
            it.from(new File(contentSrc, "META-INF/vault/definition/.content.xml"));
            it.expand(getProject().getProperties());
        });
    }


    private void addVaultFilter() {
        val addBundlesToFilterXmlTasks = getProject().getTasks().withType(AddBundlesToFilterXmlTask.class);
        if (addBundlesToFilterXmlTasks.isEmpty()) {
            filterXmlWithoutRewrite();
        }
        else {
            if (addBundlesToFilterXmlTasks.size() > 1) {
                throw new InvalidUserDataException("There are more than one " +
                    AddBundlesToFilterXmlTask.class.getName() + " tasks defined for " + getProject());
            }
            else {
                val addBundlesToFilterXmlTask = addBundlesToFilterXmlTasks.iterator().next();
                if (addBundlesToFilterXmlTask.isEnabled()) {
                    try {
                        val filterXmlFile = addBundlesToFilterXmlTask.getOutFile();
                        this.exclude("META-INF/vault/filter.xml");
                        this.into("META-INF/vault", it -> it.from(filterXmlFile));
                    }
                    catch (IOException e) {
                        throw new IllegalStateException("Could not read outFile from " + addBundlesToFilterXmlTask.getPath(), e);
                    }
                }
                else {
                    filterXmlWithoutRewrite();
                }
            }
        }
    }


    private void filterXmlWithoutRewrite() {
        try {
            final File filterXmlFile = getFilterXmlFile();
            this.exclude("META-INF/vault/filter.xml");
            this.into("META-INF/vault", it -> it.from(filterXmlFile));
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read filter.xml from " + this.getPath(), e);
        }
    }


    @InputFile
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public File getFilterXmlFile() throws IOException {
        val inFile = new File(getContentSrc(), "META-INF/vault/filter.xml");
        if (inFile.exists()) {
            LOG.info("Using filter.xml file at {} for {}", relativizeToProject(inFile), this.getPath());
            return inFile;
        }
        else {
            LOG.info("There's no filter.xml file at {} so generating a blank one for {}", relativizeToProject(inFile), this.getPath());
            val tmpFile = new File(getProject().getBuildDir(), "createPackage/filter.xml").getCanonicalFile();
            tmpFile.getParentFile().mkdirs();
            try (val writer = Files.newBufferedWriter(tmpFile.toPath())) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workspaceFilter version=\"1.0\"/>\n");
            }
            return tmpFile;
        }
    }


    private File relativizeToProject(File filePath) {
        val projPath = getProject().getProjectDir().getAbsoluteFile().toPath();
        val path = filePath.getAbsoluteFile().toPath();
        return projPath.relativize(path).toFile();
    }


    @InputFiles
    public Collection<File> getBundleFiles() {
        switch (copyBundlesMode) {
            case ALL:
                return CqPackageUtils.allBundleFiles(getProject(), getConfiguration());
            case PROJECT_ONLY:
                return CqPackageUtils.allProjectBundleJarFiles(getProject(), getConfiguration());
            case NON_PROJECT_ONLY:
                return CqPackageUtils.nonProjectDependencyBundleFiles(getConfiguration());
            case NONE:
                return Collections.emptyList();
            default:
                throw new IllegalStateException("Unknown CopyBundlesMode: " + copyBundlesMode);
        }
    }


    protected void addBundles(final String bundleInstallRoot) {
        if (copyBundlesMode.equals(NONE)) return; // nothing to do

        this.into("jcr_root" + bundleInstallRoot, spec -> {
            val files = getBundleFiles();
            LOG.info("Adding bundles: {}", files);
            spec.from(files);
        });
    }


    /**
     * Root location that jar bundles should be installed to.
     * Should include a prefixing / but not a trailing one.
     */
    @Input
    public String getBundleInstallRoot() {
        return _bundleInstallRoot;
    }


    /**
     * Root location that jar bundles should be installed to.
     */
    public void setBundleInstallRoot(String path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path == null or empty");

        String thePath = path.trim();

        if (!thePath.startsWith("/")) thePath = "/" + thePath;

        _bundleInstallRoot = (thePath.length() > 1 && thePath.endsWith("/")) ?
            thePath.substring(0, thePath.length() - 1) : thePath;
    }


    public static Configuration cqPackageConfiguration(Project project) {
        val conf = project.getConfigurations().findByName(CqPackagePlugin.CQ_PACKAGE);
        if (conf == null) {
            createCqPackageConf(project);
            return project.getConfigurations().getByName(CqPackagePlugin.CQ_PACKAGE);
        }

        return conf;
    }


    private static void createCqPackageConf(Project project) {
        LOG.debug("Creating configuration: {}", CqPackagePlugin.CQ_PACKAGE);
        val cqPackageConf = project.getConfigurations().create(CqPackagePlugin.CQ_PACKAGE);

        // attach to "runtime", but don't insist that it have to be there first (or will ever be there)
        project.getConfigurations().withType(Configuration.class, conf -> {
            if (conf.getName().equals("runtime")) {
                LOG.debug("Making {} extend from {}", CqPackagePlugin.CQ_PACKAGE, conf.getName());
                cqPackageConf.extendsFrom(conf);
            }
        });
    }


    @Input
    @SuppressWarnings("unused")
    public Collection<String> getFileExclusions() {
        return fileExclusions;
    }


    @SuppressWarnings("unused")
    public void setFileExclusions(Collection<String> fileExclusions) {
        this.fileExclusions = fileExclusions;
    }


    @Input
    @SuppressWarnings("unused")
    public CopyBundlesMode getCopyBundlesMode() {
        return copyBundlesMode;
    }


    public void setCopyBundlesMode(CopyBundlesMode copyBundlesMode) {
        this.copyBundlesMode = copyBundlesMode;
    }

}
