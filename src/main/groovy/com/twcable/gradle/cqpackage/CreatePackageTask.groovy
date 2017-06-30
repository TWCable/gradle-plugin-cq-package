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
package com.twcable.gradle.cqpackage

import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Zip

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.CqPackageUtils.allBundleFiles
import static com.twcable.gradle.cqpackage.CqPackageUtils.allProjectBundleJarFiles
import static com.twcable.gradle.cqpackage.CqPackageUtils.nonProjectDependencyBundleFiles
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.ALL
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.NONE
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.NON_PROJECT_ONLY
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.PROJECT_ONLY
import static java.util.Collections.EMPTY_LIST

@Slf4j
@TypeChecked
class CreatePackageTask extends Zip {
    private String _bundleInstallRoot = '/apps/install'
    @Internal
    private File _contentSrc
    @Internal
    private Configuration _configuration

    /**
     * Mutable collection of exclusions to apply to copying files from {@link #getContentSrc()}.
     * Defaults with a "reasonable" set, such as "*&#42;/.vlt", "*&#42;/.git/**", etc.
     * Generally to modify this list you would mutate this in-place.
     */
    @Input
    Collection<String> fileExclusions

    @Input
    CopyBundlesMode copyBundlesMode = ALL


    CreatePackageTask() {
        super()

        // AbstractTask() guarantees that this.project has been set

        setDefaults()
        addVaultFilter()

        project.afterEvaluate {
            addVaultDefinition(contentSrc)
            addBundles(bundleInstallRoot)
            addExclusions(fileExclusions)
        }
    }


    private void setDefaults() {
        setDescription("Creates the CQ Package zip file")
        setGroup("CQ")

        fileExclusions = [
            '**/.git',
            '**/.git/**',
            '**/.gitattributes',
            '**/.gitignore',
            '**/.gitmodules',
            '**/.vlt',
            'jcr_root/.vlt-sync-config.properties',
            'jcr_root/var/**',
            'SLING-INF/**',
        ]

        setContentSrc(project.file("src/main/content"))
    }


    @Nonnull
    static CreatePackageTask from(@Nonnull Project project) {
        if (project == null) throw new IllegalArgumentException("project == null")
        def tasks = project.tasks.withType(CreatePackageTask)
        if (tasks == null || tasks.isEmpty()) throw new IllegalArgumentException("${project} does not have a ${CreatePackageTask.name}")
        if (tasks.size() > 1) throw new IllegalArgumentException("${project} has more than one ${CreatePackageTask.name}")
        return tasks.first()
    }

    /**
     * All the bundles that this depends on (project and non-project) will be copied into the "install" folder.
     * This is the default behavior.
     * @see #addProjectBundles()
     * @see #addNonProjectBundles()
     * @see #addNoBundles()
     */
    void addAllBundles() {
        copyBundlesMode = ALL
    }

    /**
     * Only the project-generated bundles that this depends on will be copied into the "install" folder.
     * @see #addAllBundles()
     * @see #addProjectBundles()
     * @see #addNonProjectBundles()
     * @see #addNoBundles()
     */
    void addProjectBundles() {
        copyBundlesMode = PROJECT_ONLY
    }

    /**
     * Only the non-project generated bundles that this depends on be will copied into the "install" folder.
     * @see #addAllBundles()
     * @see #addProjectBundles()
     * @see #addNoBundles()
     */
    void addNonProjectBundles() {
        copyBundlesMode = NON_PROJECT_ONLY
    }

    /**
     * None of the bundles that this depends on will be copied into the "install" folder.
     * @see #addAllBundles()
     * @see #addProjectBundles()
     * @see #addNonProjectBundles()
     */
    void addNoBundles() {
        copyBundlesMode = NONE
    }

    /**
     * The root of the content tree for files that it will copy into the package.
     * Defaults to `project.file("src/main/content")`
     */
    @SuppressWarnings("GroovyUnusedDeclaration")
    void setContentSrc(File contentSrc) {
        if (_contentSrc != null)
            ((DefaultCopySpec)getMainSpec()).getSourcePaths().remove(this._contentSrc)

        ((DefaultCopySpec)getMainSpec()).getSourcePaths().add(contentSrc)
        this._contentSrc = contentSrc
    }

    /**
     * The root of the content tree for files that it will copy into the package.
     * Defaults to `project.file("src/main/content")`
     */
    @InputDirectory
    File getContentSrc() {
        return _contentSrc
    }


    static enum CopyBundlesMode {
        ALL, PROJECT_ONLY, NON_PROJECT_ONLY, NONE
    }


    @Input
    String getConfigurationName() {
        return getConfiguration().name
    }


    void setConfigurationName(String confName) {
        configuration = project.configurations.getByName(confName)
    }

    /**
     * The Configuration to use when determining what bundles to put in the package.
     * @see #addAllBundles()
     */
    @Internal
    Configuration getConfiguration() {
        if (_configuration != null) return _configuration

        return CqPackagePlugin.cqPackageDependencies(project)
    }


    void setConfiguration(Configuration conf) {
        this._configuration = conf
    }


    protected void addExclusions(Collection<String> fileExclusions) {
        fileExclusions.each {
            exclude it
        }
    }


    protected void addVaultDefinition(@Nonnull File contentSrc) {
        exclude 'META-INF/vault/definition/.content.xml'
        exclude 'META-INF/vault/properties.xml'

        into('META-INF/vault') {
            from new File(contentSrc, 'META-INF/vault/properties.xml')
            expand(project.properties)
        }

        into('META-INF/vault/definition') {
            from new File(contentSrc, 'META-INF/vault/definition/.content.xml')
            expand(project.properties)
        }
    }


    private void addVaultFilter() {
        this.mustRunAfter 'addBundlesToFilterXml'

        exclude 'META-INF/vault/filter.xml'
        into('META-INF/vault') {
            from project.file("${project.buildDir}/tmp/filter.xml")
        }
    }


    @InputFiles
    Collection<File> getBundleFiles() {
        switch (copyBundlesMode) {
            case ALL: return allBundleFiles(project, configuration)
            case PROJECT_ONLY: return allProjectBundleJarFiles(project, configuration)
            case NON_PROJECT_ONLY: return nonProjectDependencyBundleFiles(configuration)
            case NONE: return EMPTY_LIST
            default: throw new IllegalStateException("Unknown CopyBundlesMode: ${copyBundlesMode}")
        }
    }


    protected void addBundles(@Nonnull String bundleInstallRoot) {
        if (copyBundlesMode == NONE) return // nothing to do

        this.into("jcr_root${bundleInstallRoot}") { CopySpec spec ->
            Collection<File> files = getBundleFiles()
            logger.info "Adding bundles: ${files}"
            spec.from files
        }
    }

    /**
     * Root location that jar bundles should be installed to.
     * Should include a prefixing / but not a trailing one.
     */
    @Input
    String getBundleInstallRoot() {
        return _bundleInstallRoot
    }

    /**
     * Root location that jar bundles should be installed to.
     */
    void setBundleInstallRoot(String path) {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("path == null or empty")

        path = path.trim()

        if (path[0] != '/')
            path = "/${path}"

        _bundleInstallRoot = (path.size() > 1 && path[-1] == '/') ?
            path[0..-2] : path
    }

}
