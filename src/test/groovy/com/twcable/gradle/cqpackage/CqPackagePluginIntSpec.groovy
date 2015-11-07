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

import groovy.xml.MarkupBuilder
import nebula.test.IntegrationSpec
import org.gradle.api.logging.LogLevel
import org.spockframework.runtime.SpockAssertionError
import spock.lang.Ignore

import java.util.zip.ZipFile

class CqPackagePluginIntSpec extends IntegrationSpec {

    def setup() {
        logLevel = LogLevel.DEBUG
    }

    // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/3
    @Ignore("Currently broken: Zip file is written at project root")
    def "create package with no code and no content"() {
        createVaultMetaInf(getProjectDir())

        buildFile << """
            ${applyPlugin(CqPackagePlugin.class)}
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('createPackage')

        then:
        println result.standardOutput
        result.wasExecuted(':createPackage')
    }


    def "create package with code and no content"() {
        writeHelloWorld('com.twcable.test', getProjectDir())
        createVaultMetaInf(getProjectDir())

        buildFile << """
            ${applyPlugin(CqPackagePlugin.class)}
            apply plugin: 'java'
            apply plugin: 'osgi'
        """.stripIndent()

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        def result = runTasksSuccessfully('jar', 'createPackage')

        then:
        println result.standardOutput
        fileExists('build/classes/main/com/twcable/test/HelloWorld.class')
        result.wasExecuted(':jar')
        pathExistsInPackage(moduleName, projectDir, "META-INF/vault/filter.xml")
        pathExistsInPackage(moduleName, projectDir, "jcr_root/apps/install/${moduleName}.jar")
    }


    def "create package from subpackage with code and no content"() {
        def modADir = addSubproject('module-A', """
            ${applyPlugin(CqPackagePlugin.class)}
            apply plugin: 'java'
            apply plugin: 'osgi'
        """.stripIndent())

        writeHelloWorld('com.twcable.test', modADir)
        createVaultMetaInf(modADir)

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        def result = runTasksSuccessfully('jar', ':module-A:createPackage')

        then:
        println result.standardOutput
        fileExists('module-A/build/classes/main/com/twcable/test/HelloWorld.class')
        result.wasExecuted(':module-A:jar')
        pathExistsInPackage("module-A", modADir, "META-INF/vault/filter.xml")
        pathExistsInPackage("module-A", modADir, "jcr_root/apps/install/module-A.jar")
    }

    // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/3
    @Ignore("Currently broken: Zip file is written at project root")
    def "create package from subpackage with no code and no content, and sibling with code"() {
        def modADir = addSubproject('module-A', """
            apply plugin: 'java'
            apply plugin: 'osgi'
        """.stripIndent())

        writeHelloWorld('com.twcable.test.a', modADir)

        def modBDir = addSubproject('module-B', """
            ${applyPlugin(CqPackagePlugin.class)}

            dependencies {
                cq_package project(":module-A")
            }
        """.stripIndent())

        createVaultMetaInf(modBDir)

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        def result = runTasksSuccessfully('jar', 'createPackage')

        then:
        println result.standardOutput
        fileExists('module-A/build/classes/main/com/twcable/test/a/HelloWorld.class')
        result.wasExecuted(':module-A:jar')
        result.wasExecuted(':module-B:createPackage')
        pathExistsInPackage("module-B", modBDir, "META-INF/vault/filter.xml")
        pathExistsInPackage("module-B", modBDir, "jcr_root/apps/install/module-A.jar")
    }


    def "create package from subpackages that both have code "() {
        def modADir = addSubproject('module-A', """
            apply plugin: 'java'
            apply plugin: 'osgi'
        """.stripIndent())

        writeHelloWorld('com.twcable.test.a', modADir)

        def modBDir = addSubproject('module-B', """
            ${applyPlugin(CqPackagePlugin.class)}

            apply plugin: 'java'
            apply plugin: 'osgi'

            dependencies {
                cq_package project(":module-A")
            }
        """.stripIndent())

        writeHelloWorld('com.twcable.test.b', modBDir)
        createVaultMetaInf(modBDir)

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        def result = runTasksSuccessfully('jar', 'createPackage')

        then:
        println result.standardOutput
        fileExists('module-A/build/classes/main/com/twcable/test/a/HelloWorld.class')
        result.wasExecuted(':module-A:jar')
        result.wasExecuted(':module-B:jar')
        result.wasExecuted(':module-B:createPackage')
        pathExistsInPackage("module-B", modBDir, "META-INF/vault/filter.xml")
        pathExistsInPackage("module-B", modBDir, "jcr_root/apps/install/module-A.jar")
        pathExistsInPackage("module-B", modBDir, "jcr_root/apps/install/module-B.jar")
    }

    // ********************************************************************
    //
    // HELPER METHODS
    //
    // ********************************************************************


    boolean pathExistsInPackage(String moduleName, File basePath, String entryPath) {
        def file = file("build/distributions/${moduleName}-unspecified.zip", basePath)
        if (!file.exists()) throw new SpockAssertionError("${file.canonicalPath} does not exist")
        try {
            def zip = new ZipFile(file)
            try {
                return zip.getEntry(entryPath) != null
            }
            finally {
                zip.close()
            }
        }
        catch (Exception exp) {
            throw new SpockAssertionError("Could not read ${file.canonicalPath}", exp)
        }
    }


    protected void writeHelloWorld(String packageDotted, File baseDir = getProjectDir()) {
        def path = 'src/main/java/' + packageDotted.replace('.', '/') + '/HelloWorld.java'
        def javaFile = createFile(path, baseDir)
        javaFile << """
            package ${packageDotted};

            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello Integration Test");
                }
            }
        """.stripIndent()
    }


    void createVaultMetaInf(File baseDir) {
        def basePath = 'src/main/content/META-INF/vault'

        createXmlFile("${basePath}/config.xml", baseDir) {
            mkp.comment('blah blah')
        }

        createXmlFile("${basePath}/settings.xml", baseDir) {
            mkp.comment('blah blah')
        }

        createXmlFile("${basePath}/filter.xml", baseDir) {
            mkp.xmlDeclaration([version: "1.0", encoding: "utf-8"])
            workspaceFilter(version: "1.0") {
                filter(root: "/apps/testapp")
            }
        }

        createXmlFile("${basePath}/properties.xml", baseDir) {
            mkp.xmlDeclaration([version: "1.0", encoding: "utf-8", standalone: "no"])
            mkp.yieldUnescaped('<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">\n')
            properties(version: "1.0") {
                entry(key: "name", '${name}')
            }
        }
    }


    File createXmlFile(String filePath, File baseDir,
                       @DelegatesTo(MarkupBuilder) Closure builder) {
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        builder.delegate = xml
        builder.call()

        return createFile(filePath, baseDir) << writer.toString()
    }

}
