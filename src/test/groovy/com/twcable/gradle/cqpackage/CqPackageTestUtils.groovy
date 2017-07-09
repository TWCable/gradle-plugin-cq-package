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
import groovy.transform.TypeCheckingMode
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.Project
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.testfixtures.ProjectBuilder
import org.slf4j.impl.StaticLoggerBinder

import javax.annotation.Nonnull

@TypeChecked
final class CqPackageTestUtils {

    static Project createCqPackageProject() {
        return createCqPackageProject("2.3.4", "/apps/install")
    }


    static Project createCqPackageProject(String version, String bundleRoot) {
        Project project = ProjectBuilder.builder().build()
        logLevel = LogLevel.DEBUG
        project.version = version
        project.apply plugin: 'com.twcable.cq-package'
        project.apply plugin: 'java'

        project.tasks.withType(CreatePackageTask) { CreatePackageTask task ->
            task.bundleInstallRoot = bundleRoot
        }

        return project
    }


    static Project createSubProject(Project parentProject, String projName, boolean makeOsgi) {
        Project project = ProjectBuilder.builder().withName(projName).withProjectDir(new File(parentProject.projectDir, projName)).withParent(parentProject).build()
//        Project project = ProjectBuilder.builder().withName(projName).withParent(parentProject).build()
        project.version = parentProject.version
        project.apply plugin: 'java'
        if (makeOsgi) project.apply plugin: 'osgi'
        return project
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    @SuppressWarnings("GroovyAssignabilityCheck")
    static String xml(Closure closure) {
        def builder = new StreamingMarkupBuilder()
        return XmlUtil.serialize(builder.bind(closure))
    }


    static String xmlString(String xml) {
        XmlUtil.serialize(xml)
    }


    static void addProjectToCompile(Project project, Project projectDep) {
        addProjectToConfiguration(project, "compile", projectDep)
    }


    static void addProjectToConfiguration(Project project, String configName, Project projectDep) {
        def dependencyHandler = project.dependencies
        def compile = project.configurations.getByName(configName)
        compile.dependencies.add(dependencyHandler.project([path: projectDep.path]))
    }


    static void addCompileDependency(Project project, File file) {
        def dependencyHandler = project.dependencies
        def compile = project.configurations.getByName("compile")
        compile.dependencies.add(dependencyHandler.create(new SimpleFileCollection(file)))
    }


    static File contentDir(Project project) {
        def projectDir = project.projectDir
        def contentDir = new File(new File(new File(projectDir.canonicalFile, "src"), "main"), "content")
        if (!contentDir.exists()) {
            assert contentDir.mkdirs()
        }
        return contentDir
    }

    /**
     * "Touch" the file, making sure it both exists and its modified time is updated
     */
    static void touch(@Nonnull File afile) {
        afile.parentFile.mkdirs() // make sure its directory exists
        afile.append(new byte[0])
    }

    /**
     * Change the LogLevel used by the static instance of LoggerFactory.
     * <p>
     * Note that this affects a static factory, so changes will be reflected by anything that uses the same classloader.
     */
    static void setLogLevel(LogLevel logLevel) {
        final loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory()

        final loggerFactoryClass = loggerFactory.getClass()
        loggerFactoryClass.getMethod("setLevel", LogLevel.class).invoke(loggerFactory, logLevel)
        final eventListener = loggerFactoryClass.getMethod("getOutputEventListener").invoke(loggerFactory)
        if (eventListener == null) throw new IllegalStateException("eventListener == null for " + loggerFactory)
        eventListener.getClass().getMethod("configure", LogLevel.class).invoke(eventListener, logLevel)
    }

}
