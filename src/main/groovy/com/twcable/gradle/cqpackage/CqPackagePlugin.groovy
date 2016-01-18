/*
 * Copyright 2014-2015 Time Warner Cable, Inc.
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

import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.BasePlugin
import org.gradle.jvm.tasks.Jar

import static com.twcable.gradle.GradleUtils.extension
import static com.twcable.gradle.cqpackage.CqPackageHelper.isBadResponse

/**
 * <h1>Plugin name</h1>
 * "com.twcable.cq-package"
 *
 * <h1>Description</h1>
 * Adds tasks for working with CQ/Jackrabbit/Sling Packages.
 * <p>
 * See <a href="https://github.com/TWCable/gradle-plugin-cq-package/blob/master/docs/CqPackagePlugin.adoc">the plugin
 * documentation</a> for specifics on how this is expected to work.
 */
@Slf4j
@TypeChecked
@SuppressWarnings(["GroovyResultOfAssignmentUsed", "GrMethodMayBeStatic"])
class CqPackagePlugin implements Plugin<Project> {

    public static final String CQ_PACKAGE = 'cq_package'


    @Override
    void apply(Project project) {
        log.info("Applying ${this.class.name} to ${project}")

        project.plugins.apply(BasePlugin)

        addDepConfiguration(project)

        extension(project, CqPackageHelper, project)
        extension(project, SlingServersConfiguration, project)

        addTasks(project)
        // GradleUtils.taskDependencyGraph(project)
    }


    @SuppressWarnings("GroovyAssignabilityCheck")
    private void addDepConfiguration(Project project) {
        log.debug("Creating configuration: ${CQ_PACKAGE}")
        def cqPackageConf = project.configurations.create CQ_PACKAGE

        // attach to "runtime", but don't insist that it have to be there first (or will ever be there)
        // TODO: Implement with .withType instead of explicit watchers
        def runtimeConf = project.configurations.findByName("runtime")
        if (runtimeConf == null) {
            project.configurations.whenObjectAdded {
                if (it instanceof Configuration) {
                    Configuration conf = (Configuration)it
                    if (conf.name == "runtime") {
                        log.debug("Making ${CQ_PACKAGE} extend from ${conf.name}")
                        cqPackageConf.extendsFrom(conf)
                    }
                }
            }
        }
        else {
            log.debug("Making ${CQ_PACKAGE} extend from ${runtimeConf.name}")
            cqPackageConf.extendsFrom(runtimeConf)
        }
    }


    private void addTasks(Project project) {
        log.debug "Adding tasks for ${this.class.name} to ${project}"

        def verifyBundles = verifyBundles(project)
        def addBundlesToFilterXml = addBundlesToFilterXml(project)
        createPackage(project, addBundlesToFilterXml, verifyBundles)

        uninstallBundles(project)
        uninstallPackage(project)
        removePackage(project)
        uploadPackage(project)
        installPackage(project)
        validateBundles(project)
        validateRemoteBundles(project)
        startInactiveBundles(project)

        project.logger.debug "Finished adding tasks for ${this.class.name} to ${project}"
    }


    private Task startInactiveBundles(Project project) {
        return project.tasks.create('startInactiveBundles').with {
            description = "Asynchronously attempts to start any bundle in a RESOLVED state."
            group = 'CQ'

            doLast {
                cqPackageHelper(project).startInactiveBundles()
            }

            mustRunAfter 'installPackage', 'uninstallPackage'
        }
    }


    private Task verifyBundles(Project project) {
        return project.tasks.create('verifyBundles', VerifyBundlesTask).with {
            description = "Checks all the JARs that are included in this package to make sure they are OSGi " +
                "compliant, and gives a report of any that are not. Never causes the build to fail."
            group = 'CQ'
            it
        }
    }


    private void installPackage(Project project) {
        project.tasks.create('installPackage').with {
            description = "Installs the CQ Package that has been uploaded"
            group = 'CQ'

            doLast {
                cqPackageHelper(project).installPackage(SimpleSlingPackageSupportFactory.INSTANCE)
            }

            mustRunAfter 'uploadPackage'
        }
    }


    Task uploadPackage(Project project) {
        return project.tasks.create('uploadPackage').with {
            description = "Uploads the CQ Package"
            group = 'CQ'

            doLast {
                def status = cqPackageHelper(project).uploadPackage(SimpleSlingPackageSupportFactory.INSTANCE)
                if (status != Status.OK) throw new GradleException(status.name)
            }

            dependsOn 'removePackage'
            mustRunAfter 'createPackage', 'uninstallPackage'
        }
    }


    Task removePackage(Project project) {
        return project.tasks.create('removePackage').with {
            description = "Removes the package from CQ. Does not fail if the package was not on " +
                "the server to begin with."
            group = 'CQ'

            doLast {
                cqPackageHelper(project).deletePackage(SimpleSlingPackageSupportFactory.INSTANCE)
            }

            dependsOn 'uninstallPackage'
        }
    }


    Task uninstallPackage(Project project) {
        return project.tasks.create('uninstallPackage').with {
            description = "Uninstalls the CQ Package. If the package is not on the server, nothing happens. " +
                "(i.e., This does not fail if the package is not on the server.)"
            group = 'CQ'

            doLast {
                def packageHelper = cqPackageHelper(project)
                packageHelper.uninstallPackage(SimpleSlingPackageSupportFactory.INSTANCE)
            }

            mustRunAfter 'uninstallBundles'
        }
    }


    UninstallBundlesTask uninstallBundles(Project project) {
        return project.tasks.create('uninstallBundles', UninstallBundlesTask).with {
            description = "Downloads the currently installed package .zip file if it exists, compiles list of " +
                "bundles based off what is currently installed, then stops and uninstalls each bundle individually."
            group = 'CQ'
            it
        }
    }


    AddBundlesToFilterXmlTask addBundlesToFilterXml(Project project) {
        return project.tasks.create('addBundlesToFilterXml', AddBundlesToFilterXmlTask).with {
            description = "Adds the bundles to the filter.xml"
            group = 'CQ'
            it
        }
    }


    Task validateRemoteBundles(Project project) {
        return project.tasks.create('validateRemoteBundles').with {
            description = "Validates remote bundles in the CQ Package are started correctly"
            group = 'CQ'

            doLast {
                def resp = cqPackageHelper(project).validateRemoteBundles()
                if (isBadResponse(resp.code, false)) throw new GradleException("Could not validate bundles: ${resp}")
            }
        }
    }


    Task validateBundles(Project project) {
        return project.tasks.create('validateBundles').with {
            description = "Checks all the JARs that are included in the package to make sure they are " +
                "installed and in an ACTIVE state and gives a report of any that are not. This task polls in the " +
                "case the bundles are newly installed."
            group = 'CQ'

            doLast {
                def packageHelper = cqPackageHelper(project)
                def packageDependencies = cqPackageDependencies(project)
                def resp = packageHelper.validateBundles(packageDependencies)
                if (isBadResponse(resp.code, false)) throw new GradleException("Could not validate bundles: ${resp}")
            }

            dependsOn cqPackageDependencies(project)
        }
    }


    CreatePackageTask createPackage(Project project, AddBundlesToFilterXmlTask addBundlesToFilterXmlTask, Task verifyBundles) {
        def cp = project.tasks.create("createPackage", CreatePackageTask).with { cpTask ->
            project.tasks.withType(Jar) {
                cpTask.mustRunAfter 'jar'
            }

            cpTask.dependsOn verifyBundles, addBundlesToFilterXmlTask
        } as CreatePackageTask

        // add to the publication set, so it's picked up by things like the "assemble" task
        def pubSet = (DefaultArtifactPublicationSet)project.extensions.getByType(DefaultArtifactPublicationSet)
        pubSet.addCandidate(new ArchivePublishArtifact(cp))

        return cp
    }


    static Configuration cqPackageDependencies(Project project) {
        return project.configurations.getByName(CQ_PACKAGE)
    }


    private static CqPackageHelper cqPackageHelper(Project project) {
        return (CqPackageHelper)extension(project, CqPackageHelper)
    }

}
