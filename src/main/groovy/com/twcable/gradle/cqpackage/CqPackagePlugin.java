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

import com.twcable.gradle.GradleUtils;
import com.twcable.gradle.sling.SlingServersConfiguration;
import lombok.val;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * <h1>Plugin name</h1>
 * "com.twcable.cq-package"
 * <p>
 * <h1>Description</h1>
 * Adds tasks for working with CQ/Jackrabbit/Sling Packages.
 * <p>
 * See <a href="https://github.com/TWCable/gradle-plugin-cq-package/blob/master/docs/CqPackagePlugin.adoc">the plugin
 * documentation</a> for specifics on how this is expected to work.
 */
@SuppressWarnings("Convert2MethodRef")
public class CqPackagePlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(CqPackagePlugin.class);

    public static final String NAME = "com.twcable.cq-package";
    public static final String CQ_PACKAGE = "cq_package";


    @Override
    public void apply(final Project project) {
        LOG.info("Applying {} to {}", this.getClass().getName(), project);

        project.getPlugins().apply(CqPackageBasePlugin.class);

        GradleUtils.extension(project, CqPackageHelper.class, project);
        GradleUtils.extension(project, SlingServersConfiguration.class, project);

        addTasks(project);
        // GradleUtils.taskDependencyGraph(project)
    }


    private void addTasks(final Project project) {
        LOG.debug("Adding tasks for {} to {}", this.getClass().getName(), project);

        val tasks = project.getTasks();

        val verifyBundles = tasks.create("verifyBundles", VerifyBundlesTask.class);
        val addBundlesToFilterXml = tasks.create("addBundlesToFilterXml", AddBundlesToFilterXmlTask.class);
        val createPackage = tasks.create("createPackage", CreatePackageTask.class);
        val uninstallBundles = tasks.create("uninstallBundles", UninstallBundlesTask.class);
        val uninstallPackage = tasks.create("uninstallPackage", UninstallPackageTask.class);
        val removePackage = tasks.create("removePackage", RemovePackageTask.class);
        val uploadPackage = tasks.create("uploadPackage", UploadPackageTask.class);
        val installPackage = tasks.create("installPackage", InstallPackageTask.class);
        val validateBundles = tasks.create("validateBundles", ValidateBundlesTask.class);
        val validateRemoteBundles = tasks.create("validateRemoteBundles", ValidateRemoteBundlesTask.class);
        val startInactiveBundles = tasks.create("startInactiveBundles", StartInactiveBundlesTask.class);

        removePackage.dependsOn(uninstallPackage);
        uploadPackage.dependsOn(removePackage);

        uninstallPackage.mustRunAfter(uninstallBundles);
        uploadPackage.mustRunAfter(createPackage, uninstallPackage);
        installPackage.mustRunAfter(uploadPackage, uninstallPackage);
        startInactiveBundles.mustRunAfter(installPackage, uninstallPackage);

        validateBundles.bundles(createPackage.getConfiguration());

        addBundlesToFilterXml.setCreatePackageTask(createPackage);

        val pubSet = project.getExtensions().getByType(DefaultArtifactPublicationSet.class);
        pubSet.addCandidate(new ArchivePublishArtifact(createPackage));

        Arrays.asList(verifyBundles, addBundlesToFilterXml, validateBundles, installPackage, uploadPackage,
            validateRemoteBundles, startInactiveBundles, removePackage, uninstallBundles, uninstallPackage,
            createPackage
        ).forEach(task -> task.setGroup("CQ"));

        LOG.debug("Finished adding tasks for " + this.getClass().getName() + " to " + project);
    }


    public static CqPackageHelper cqPackageHelper(Project project) {
        return GradleUtils.extension(project, CqPackageHelper.class);
    }

}
