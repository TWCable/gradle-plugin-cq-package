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

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import nebula.test.ProjectSpec
import org.gradle.api.logging.LogLevel
import spock.lang.Subject
import spock.lang.Unroll

import java.util.zip.ZipFile

import static com.twcable.gradle.GradleUtils.execute
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addProjectToConfiguration
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.contentDir
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createSubProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.logLevel
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.touch

@Subject(CreatePackageTask)
class CreatePackageTaskSpec extends ProjectSpec {

    def setup() {
        logLevel = LogLevel.DEBUG
        project.version = "2.3.4"

        project.configurations.create("fooble")
        def createPackage = project.tasks.create("createPackage", CreatePackageTask)
        project.tasks.create("addBundlesToFilterXmlTask", AddBundlesToFilterXmlTask)

        createPackage.configurationName = "fooble"
        createPackage.bundleInstallRoot = "/apps/install"
        createPackage.baseName = project.name
        createPackage.destinationDir = project.file("build/cq-packages")

        final subproject1 = createSubProject(project, 'subproject1', true)
        addProjectToConfiguration(project, "fooble", subproject1)
        def subprojJarFile = new File(subproject1.buildDir, "libs/subproject1.jar").canonicalFile
        touch(subprojJarFile)
        subproject1.jar.destinationDir = subprojJarFile.parentFile
        subproject1.jar.archiveName = subprojJarFile.name
        writeHelloWorld("testing", subproject1.projectDir)

        def contentDir = contentDir(project)

        touch(new File(contentDir, "afile.txt"))
    }


    def cleanup() {
        assert project.projectDir.deleteDir()
    }


    def "create package with all bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addAllBundles()

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("META-INF/vault/filter.xml")
        filenames.contains("jcr_root/apps/install/subproject1.jar")
    }

    // https://github.com/TWCable/gradle-plugin-cq-package/issues/26
    def "create package without addBundlesToFilterXml still has filter.xml"() {
        def createPackage = project.createPackage as CreatePackageTask

        (project.addBundlesToFilterXmlTask as AddBundlesToFilterXmlTask).enabled = false

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        filenames.contains("META-INF/vault/filter.xml")
    }


    def "create package with no bundle option set"() {
        def createPackage = project.createPackage as CreatePackageTask

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("META-INF/vault/filter.xml")
        filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with only project bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addProjectBundles()

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("META-INF/vault/filter.xml")
        filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with only non-project bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addNonProjectBundles()

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("META-INF/vault/filter.xml")
        !filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with no bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addNoBundles()

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        filenames.contains("afile.txt")
        !filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "the file exclusion filter is working"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addNoBundles()
        def contentDir = contentDir(project)
        touch(new File(contentDir, ".gitignore"))
        touch(new File(contentDir, "jcr_root/.vlt-sync-config.properties"))

        when:
        execute(createPackage)
        def filenames = filesInZip(createPackage)

        then:
        !filenames.contains(".gitignore")
        filenames.contains("META-INF/vault/filter.xml")
        !filenames.contains("jcr_root/.vlt-sync-config.properties")
    }


    @Unroll
    def 'check bundleInstallRoot: #startBundleInstall'() {
        given:
        def createPackage = project.createPackage as CreatePackageTask

        if (startBundleInstall != null)
            createPackage.bundleInstallRoot = startBundleInstall

        expect:
        createPackage.bundleInstallRoot == bundleInstallRoot

        where:
        startBundleInstall | bundleInstallRoot
        null               | '/apps/install'
        '/my/app/root'     | '/my/app/root'
        'my/app/root'      | '/my/app/root'
        '/my/app/root/'    | '/my/app/root'
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************

    static List<String> filesInZip(CreatePackageTask createPackage) {
        def createPackageFile = createPackage.archivePath
        def zipFile = new ZipFile(createPackageFile)
        def iterator = zipFile.entries().iterator()
        return iterator.collect { entry -> entry.name }
    }


    protected File directory(String path, File baseDir = getProjectDir()) {
        new File(baseDir, path).with {
            mkdirs()
            it
        }
    }


    protected File file(String path, File baseDir) {
        def splitted = path.split('/')
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join('/'), baseDir) : baseDir
        def file = new File(directory, splitted[-1])
        file.createNewFile()
        file
    }


    @CompileStatic(TypeCheckingMode.SKIP)
    protected File createFile(String path, File baseDir) {
        File file = file(path, baseDir)
        if (!file.exists()) {
            assert file.parentFile.mkdirs() || file.parentFile.exists()
            file.createNewFile()
        }
        file
    }


    protected void writeHelloWorld(String packageDotted, File baseDir) {
        def path = 'src/main/java/' + packageDotted.replace('.', '/') + '/HelloWorld.java'
        def javaFile = createFile(path, baseDir)
        javaFile << """\
            package ${packageDotted};
        
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello Integration Test");
                }
            }
            """.stripIndent()
    }

}
