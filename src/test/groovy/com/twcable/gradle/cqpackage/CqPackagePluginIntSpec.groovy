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

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.ImmutableMap
import com.twcable.gradle.http.HttpResponse
import groovy.json.JsonBuilder
import groovy.transform.Immutable
import groovy.transform.TypeChecked
import groovy.xml.MarkupBuilder
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import nebula.test.functional.GradleRunner
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.util.MultiPartInputStreamParser
import org.gradle.api.logging.LogLevel
import org.spockframework.runtime.SpockAssertionError
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Subject

import javax.servlet.MultipartConfigElement
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.zip.ZipFile

import static com.twcable.gradle.cqpackage.PackageServerFixture.successfulDeletePackage
import static com.twcable.gradle.cqpackage.PackageServerFixture.successfulInstallPackage
import static com.twcable.gradle.cqpackage.PackageServerFixture.successfulPackageUpload
import static com.twcable.gradle.cqpackage.PackageServerFixture.successfulUninstallPackage

@Subject(CqPackagePlugin)
class CqPackagePluginIntSpec extends IntegrationSpec {

    @AutoCleanup("stop")
    Server server

    HandlerCollection handlerCollection

    GetHandler getHandler = new GetHandler()
    PostHandler postHandler = new PostHandler()

    String projVersion = "12.3"

    ExecutionResult result


    def setup() {
        logLevel = LogLevel.DEBUG
        classpathFilter = Predicates.or(
            GradleRunner.CLASSPATH_DEFAULT,
            { URL url -> url.path.contains('/.m2/repository/') } as Predicate<URL>
        )

        createAndStartServer()
    }


    void cleanup() {
        if (result != null) {
            println result.standardOutput
            println result.standardError
        }
    }


    def "smoke task config"() {
        logLevel = LogLevel.INFO
        createVaultMetaInf(projectDir)

        buildFile << """
            ${applyPlugin(CqPackagePlugin.class)}
            version = '${projVersion}'
        """.stripIndent()

        when:
        result = runTasksSuccessfully('tasks', '--all')

        then:
        // will fail if the tasks have invalid dependency configurations, etc
        result.success
    }

    // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/3
    @Ignore("Currently broken: Zip file is written at project root")
    def "create package with no code and no content"() {
        createVaultMetaInf(projectDir)

        buildFile << """
            ${applyPlugin(CqPackagePlugin.class)}
            version = '${projVersion}'
        """.stripIndent()

        when:
        result = runTasksSuccessfully('createPackage')

        then:
        result.wasExecuted(':createPackage')
        result.success
    }


    def "create package with code and no content"() {
        writeHelloWorld('com.twcable.test', projectDir)
        createVaultMetaInf(projectDir)

        buildFile << """
            ${applyPlugin(CqPackagePlugin.class)}
            apply plugin: 'java'
            apply plugin: 'osgi'
            version = '${projVersion}'
        """.stripIndent()

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        result = runTasksSuccessfully('jar', 'createPackage')

        then:
        fileExists('build/classes/main/com/twcable/test/HelloWorld.class')
        result.wasExecuted(':jar')
        pathExistsInPackage(moduleName, projectDir, "META-INF/vault/filter.xml")
        pathExistsInPackage(moduleName, projectDir, "jcr_root/apps/install/${moduleName}-${projVersion}.jar")
        result.success
    }


    def "create package from subpackage with code and no content"() {
        def modADir = addSubproject('module-A', """
            ${applyPlugin(CqPackagePlugin.class)}
            apply plugin: 'java'
            apply plugin: 'osgi'
            version = '${projVersion}'
        """.stripIndent())

        writeHelloWorld('com.twcable.test', modADir)
        createVaultMetaInf(modADir)

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        result = runTasksSuccessfully('jar', ':module-A:createPackage')

        then:
        fileExists('module-A/build/classes/main/com/twcable/test/HelloWorld.class')
        result.wasExecuted(':module-A:jar')
        pathExistsInPackage("module-A", modADir, "META-INF/vault/filter.xml")
        pathExistsInPackage("module-A", modADir, "jcr_root/apps/install/module-A-${projVersion}.jar")
        result.success
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
            version = '${projVersion}'

            dependencies {
                cq_package project(":module-A")
            }
        """.stripIndent())

        createVaultMetaInf(modBDir)

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        result = runTasksSuccessfully('jar', 'createPackage')

        then:
        fileExists('module-A/build/classes/main/com/twcable/test/a/HelloWorld.class')
        result.wasExecuted(':module-A:jar')
        result.wasExecuted(':module-B:createPackage')
        pathExistsInPackage("module-B", modBDir, "META-INF/vault/filter.xml")
        pathExistsInPackage("module-B", modBDir, "jcr_root/apps/install/module-A.jar")
        result.success
    }


    def "create package from subpackages that both have code "() {
        def modADir = addSubproject('module-A', """
            apply plugin: 'java'
            apply plugin: 'osgi'
            version = '${projVersion}'
        """.stripIndent())

        writeHelloWorld('com.twcable.test.a', modADir)

        def modBDir = addSubproject('module-B', """
            ${applyPlugin(CqPackagePlugin)}

            apply plugin: 'java'
            apply plugin: 'osgi'
            version = '${projVersion}'

            dependencies {
                cq_package project(":module-A")
            }
        """.stripIndent())

        writeHelloWorld('com.twcable.test.b', modBDir)
        createVaultMetaInf(modBDir)

        when:
        // TODO: https://github.com/TWCable/gradle-plugin-cq-package/issues/2
        result = runTasksSuccessfully('jar', 'createPackage')

        then:
        fileExists('module-A/build/classes/main/com/twcable/test/a/HelloWorld.class')
        result.wasExecuted(':module-A:jar')
        result.wasExecuted(':module-B:jar')
        result.wasExecuted(':module-B:createPackage')
        pathExistsInPackage("module-B", modBDir, "META-INF/vault/filter.xml")
        pathExistsInPackage("module-B", modBDir, "jcr_root/apps/install/module-A-${projVersion}.jar")
        pathExistsInPackage("module-B", modBDir, "jcr_root/apps/install/module-B-${projVersion}.jar")
    }


    def "upload package with createPackage"() {
        defaultPackageListHandler()
        postHandler.addFileResponse("/crx/packmgr/service/.json", successfulPackageUpload().body)

        writeHelloWorld('com.twcable.test', projectDir)
        createVaultMetaInf(projectDir)

        writeSimpleBuildFile()

        when:
        // 'createPackage' being last in this list verifies the 'mustRunAfter' relationship
        result = runTasks("upload", "createPackage", "-x", "remove")

        then:
        result.success
        result.wasExecuted(':createPackage')
    }


    def "upload package without createPackage"() {
        defaultPackageListHandler()
        postHandler.addFileResponse("/crx/packmgr/service/.json", successfulPackageUpload().body)

        writeSimpleBuildFile()

        when:
        result = runTasks("upload", "-x", "remove")

        then:
        !result.success
        def exp = result.failure.cause
        exp.task.name == 'upload'
        exp.cause.message.contains("there is no output from the 'createPackage' task")
    }


    def "upload package with package property"() {
        def testPackageFilename = this.class.classLoader.getResource("testpackage-1.0.1.zip").file
        System.setProperty('package', testPackageFilename)

        getHandler.addPathResponse("/crx/packmgr/list.jsp",
            new JsonBuilder(
                PackageServerFixture.packageList(
                    PackageFixture.of("twc/test:testpackage:1.0.1")
                )
            ).toString()
        )

        postHandler.addFileResponse("/crx/packmgr/service/.json", successfulPackageUpload().body)

        writeSimpleBuildFile()

        when:
        result = runTasks("upload", "-x", "remove")

        then:
        result.success
        !result.wasExecuted(':createPackage')

        cleanup:
        System.clearProperty('package')
    }


    def "install package"() {
        defaultPackageListHandler()

        addPackageCommandResponse('install', successfulInstallPackage())

        writeSimpleBuildFile()

        when:
        result = runTasks("installPackage")

        then:
        result.success
        result.standardOutput.contains("\"${moduleName}\" is installed on author")
    }


    def "remove package"() {
        defaultPackageListHandler()

        def testPackageFilename = this.class.classLoader.getResource("testpackage-1.0.1.zip").file
        getHandler.addFileResponse("/crx/packmgr/download.jsp", new File(testPackageFilename))

        postHandler.addFileResponse("/crx/packmgr/service/.json", successfulPackageUpload().body)
        addPackageCommandResponse('uninstall', successfulUninstallPackage())
        addPackageCommandResponse('delete', successfulDeletePackage())

        writeSimpleBuildFile()

        when:
        result = runTasks("remove")

        then:
        result.success
    }


    def "remove package that is not installed"() {
        emptyPackageListHandler()

        writeSimpleBuildFile()

        when:
        result = runTasks("remove")

        then:
        result.success
        result.standardOutput.contains("so no need to delete it")
    }


    def "uninstall package"() {
        defaultPackageListHandler()

        def testPackageFilename = this.class.classLoader.getResource("testpackage-1.0.1.zip").file
        getHandler.addFileResponse("/crx/packmgr/download.jsp", new File(testPackageFilename))

        addPackageCommandResponse('uninstall', successfulUninstallPackage())

        writeSimpleBuildFile()

        when:
        result = runTasks("uninstall")

        then:
        result.success
    }


    def "uninstall bundles"() {
        defaultPackageListHandler()

        def testPackageFilename = this.class.classLoader.getResource("testpackage-1.0.1.zip").file
        getHandler.addFileResponse("/crx/packmgr/download.jsp", new File(testPackageFilename))

        writeSimpleBuildFile()

        when:
        result = runTasks("uninstallBundles")

        then:
        result.success
    }


    @Ignore("Test not implemented yet")
    def "start inactive bundles"() {
//        getHandler.addPathResponse(bundleControlPath, installedBundleJson)

        writeSimpleBuildFile()

        when:
        result = runTasks("startInactiveBundles")

        then:
        result.success
    }


    @Ignore("Test not implemented yet")
    def "validate bundles"() {
//        getHandler.addPathResponse(bundleControlPath, installedBundleJson)

        writeSimpleBuildFile()

        when:
        result = runTasks("validateBundles")

        then:
        result.success
    }


    @Ignore("Test not implemented yet")
    def "validate remote bundles"() {
        defaultPackageListHandler()
//        getHandler.addPathResponse(bundleControlPath, installedBundleJson)

        writeSimpleBuildFile()

        when:
        result = runTasks("validateRemoteBundles")

        then:
        result.success
    }

    // ********************************************************************
    //
    // HELPER METHODS
    //
    // ********************************************************************


    @TypeChecked
    private File writeSimpleBuildFile() {
        return buildFile << """
            apply plugin: 'java'
            ${applyPlugin(CqPackagePlugin)}
            version = '${projVersion}'
            slingServers.publisher.active = false
        """.stripIndent()
    }


    @TypeChecked
    boolean pathExistsInPackage(String moduleName, File basePath, String entryPath) {
        def file = file("build/distributions/${moduleName}-${projVersion}.zip", basePath)
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


    @TypeChecked
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
            vaultfs(version: "1.1") {
                aggregates()
                handlers()
            }
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
                entry(key: "group", 'twc/test')
                entry(key: "name", '${name}')
                entry(key: "version", '${version}')
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


    @TypeChecked
    void createAndStartServer() {
        server = new Server(0)
        handlerCollection = new HandlerCollection()

        // needed for multipart form POST parsing to work
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement(projectDir.absolutePath))
            }
        })
        addGetHandler(getHandler)
        addPostHandler(postHandler)

        server.setHandler(handlerCollection)

        server.start()
    }


    @TypeChecked
    int getServerPort() {
        return ((ServerConnector)server.connectors[0]).localPort
    }


    @TypeChecked
    void addPackageCommandResponse(String command, HttpResponse resp) {
        postHandler.addPathAndParamResponse(
            "/crx/packmgr/service/.json/etc/packages/twc/test/${moduleName}-${projVersion}",
            ImmutableMap.of('cmd', command),
            resp.body)
    }


    @TypeChecked
    private defaultPackageListHandler() {
        getHandler.addPathResponse("/crx/packmgr/list.jsp",
            new JsonBuilder(
                PackageServerFixture.packageList(
                    PackageFixture.of("twc/test:${moduleName}:${projVersion}")
                )
            ).toString()
        )
    }


    @TypeChecked
    private emptyPackageListHandler() {
        getHandler.addPathResponse("/crx/packmgr/list.jsp",
            new JsonBuilder(
                PackageServerFixture.packageList()
            ).toString()
        )
    }


    @Override
    @TypeChecked
    ExecutionResult runTasks(String... taskNames) {
        def tasksWithServerPort = (taskNames.toList() << "-Pslingserver.author.port=${serverPort}".toString()).toArray() as String[]
        super.runTasks(tasksWithServerPort)
    }


    @TypeChecked
    void addGetHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "GET") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    @TypeChecked
    void addPostHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "POST") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    @TypeChecked
    void addDeleteHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "DELETE") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    @TypeChecked
    static boolean hasFile(HttpServletRequest request) {
        // make sure the request is fully parsed
        request.getParameterMap()

        def attribute = request.getAttribute("org.eclipse.jetty.multiPartInputStream") as MultiPartInputStreamParser
        return attribute.parts.find { it.submittedFileName != null } != null
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    static interface SimpleHandler {
        void handle(HttpServletRequest request, HttpServletResponse response)
    }


    @TypeChecked
    static class GetHandler implements SimpleHandler {
        Map<String, String> pathToResponse = [:]
        Map<String, File> pathToFile = [:]


        void addPathResponse(String path, String response) {
            pathToResponse.put(path, response)
        }


        void addFileResponse(String path, File file) {
            pathToFile.put(path, file)
        }


        @Override
        void handle(HttpServletRequest request, HttpServletResponse response) {
            def respStr = pathToResponse.find { it.key == request.pathInfo }?.value
            if (respStr != null) {
                response.writer.println(respStr)
                return
            }
            def respFile = pathToFile.find { it.key == request.pathInfo }?.value
            if (respFile != null) {
                FileInputStream fileReader = new FileInputStream(respFile)
                fileReader.eachByte(4 * 1024) { byte[] buffer, count ->
                    response.outputStream.write(buffer, 0, count as int)
                }
                response.flushBuffer()
                response.status = 200
                return
            }
            response.status = 404
        }
    }


    @TypeChecked
    static class PostHandler implements SimpleHandler {
        Map<RequestPredicate, String> predToResponse = [:]


        void addPathAndParamResponse(String path, Map params, String resp) {
            predToResponse.put(new PathAndParamPredicate(path: path, params: params), resp)
        }


        void addFileResponse(String path, String resp) {
            predToResponse.put(new PathAndFilePredicate(path), resp)
        }


        @Override
        void handle(HttpServletRequest request, HttpServletResponse response) {
            def respStr = predToResponse.find { it.key.eval(request) }?.value
            if (respStr != null) {
                response.writer.println(respStr)
                return
            }
            response.status = 404
        }
    }


    static interface RequestPredicate {
        boolean eval(HttpServletRequest request)
    }


    @Immutable
    @TypeChecked
    static class PathAndParamPredicate implements RequestPredicate {
        String path
        Map params


        boolean eval(HttpServletRequest request) {
            if (hasFile(request)) return false // ignore file streams

            if (request.pathInfo == path) {
                return params.every { request.getParameter(it.key as String) == it.value }
            }
            return false
        }
    }


    @Immutable
    @TypeChecked
    static class PathAndFilePredicate implements RequestPredicate {
        String path


        boolean eval(HttpServletRequest request) {
            return request.pathInfo == path && hasFile(request)
        }
    }

}
