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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.sling.SimpleSlingSupportFactory
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import com.twcable.gradle.sling.SlingSupportFactory
import com.twcable.gradle.sling.osgi.BundleServerConfiguration
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import com.twcable.gradle.sling.osgi.SlingBundleSupport
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.jackrabbit.vault.packaging.PackageId
import org.apache.jackrabbit.vault.packaging.PackageManager
import org.apache.jackrabbit.vault.packaging.PackageProperties
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration as GradleClasspathConfiguration
import org.gradle.api.artifacts.ResolvedConfiguration

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

import static com.twcable.gradle.sling.SlingSupport.block
import static com.twcable.gradle.sling.osgi.BundleState.ACTIVE
import static com.twcable.gradle.sling.osgi.BundleState.FRAGMENT
import static com.twcable.gradle.sling.osgi.BundleState.INSTALLED
import static com.twcable.gradle.sling.osgi.BundleState.MISSING
import static com.twcable.gradle.sling.osgi.BundleState.RESOLVED
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_OK

/**
 * Has external dependencies on {@link SlingServersConfiguration}
 */
@Slf4j
@CompileStatic
@SuppressWarnings("GrMethodMayBeStatic")
class CqPackageHelper {
    public static final String NAME = 'cqPkgHelper'

    @SuppressWarnings("GrFinalVariableAccess")
    final Project project

    PackageManager packageManager = new PackageManagerImpl()

    // TODO remove Project dependency
    CqPackageHelper(Project project) {
        if (project == null) throw new GradleException('project == null')
        this.project = project
    }


    private SlingServersConfiguration slingServersConfiguration() {
        project.extensions.getByType(SlingServersConfiguration)
    }

    /**
     * Returns the package name. Uses the project's name.
     */
    // TODO disentangle project name from package name
    // https://github.com/TWCable/gradle-plugin-cq-package/issues/8
    String getPackageName() {
        return project.name
    }


    void installPackage(SlingPackageSupportFactory factory) {
        InstallPackage.install(packageName, slingServersConfiguration(), factory)
    }


    void uninstallPackage(SlingPackageSupportFactory factory) {
        UninstallPackage.uninstall(packageName, slingServersConfiguration(), factory)
    }


    void deletePackage(SlingPackageSupportFactory factory) {
        DeletePackage.delete(packageName, slingServersConfiguration(), factory)
    }

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     *
     * @return null only if the server timed out (in which case "active" is disabled on the passed serverConfig)
     */
    @Nullable
    Collection<RuntimePackageProperties> listPackages(SlingPackageSupport slingPackageSupport) {
        def serverConf = slingPackageSupport.packageServerConf.serverConf
        if (!serverConf.active) return null

        def packagesSF = ListPackages.listPackages(slingPackageSupport)
        if (packagesSF.error != null) {
            switch (packagesSF.error) {
                case Status.SERVER_TIMEOUT:
                    serverConf.active = false; return null
                default: throw new GradleException("Unknown status from listing packages: ${packagesSF.error}")
            }
        }
        return packagesSF.value
    }

    /**
     * Uploads the Package for the current Project to all the servers.
     *
     * @param factory strategy for creating SlingPackageSupport instances
     * @return the "aggregated" status: {@link Status#OK}, {@link PackageStatus#UNRESOLVED_DEPENDENCIES} or {@link PackageStatus#NO_PACKAGE}
     */
    @Nonnull
    Status uploadPackage(@Nonnull SlingPackageSupportFactory factory) {
        if (factory == null) throw new IllegalArgumentException("factory == null")
        File sourceFile = UploadPackage.getThePackageFile(project)

        final slingServersConfiguration = slingServersConfiguration()

        def status = PackageStatus.OK
        def serversIter = slingServersConfiguration.iterator()
        while (serversIter.hasNext() && status == Status.OK) {
            SlingServerConfiguration serverConfig = serversIter.next()
            def uploadStatus = UploadPackage.upload(sourceFile, false, factory.create(serverConfig), packageManager)
            if (uploadStatus == PackageStatus.UNRESOLVED_DEPENDENCIES || PackageStatus.NO_PACKAGE) status = uploadStatus
        }

        return status
    }

    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @throws IOException if it can't read the file
     */
    @Nonnull
    static PackageProperties packageProperties(File packageFile) throws IOException {
        final packageManager = new PackageManagerImpl()
        final vltPck = packageManager.open(packageFile)

        return vltPck.properties
    }

    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @see #packageProperties(File)
     * @throws IOException if it can't read the file
     */
    @Nonnull
    static PackageId packageId(File packageFile) {
        return packageProperties(packageFile).getId()
    }

    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @see #packageId(File)
     * @throws IOException if it can't read the file
     */
    @Nonnull
    static String packageName(File packageFile) {
        return packageId(packageFile).name
    }

    /**
     * Calls {@link CqPackageHelper#startInactiveBundles(SlingSupport)} for
     * each server in {@link SlingServersConfiguration}
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    HttpResponse startInactiveBundles() {
        return doAcrossServers(false) { SlingSupport slingSupport ->
            return startInactiveBundles(slingSupport)
        }
    }

    /**
     * Runs the given server action across all the active servers
     *
     * @param missingIsOk is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    private HttpResponse doAcrossServers(boolean missingIsOk,
                                         ServerAction serverAction) {
        return doAcrossServers(slingServersConfiguration(), missingIsOk, serverAction)
    }

    /**
     * Runs the given server action across all the provided active servers
     *
     * @param servers the collection of servers to run the action across
     * @param missingIsOk is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    private static HttpResponse doAcrossServers(SlingServersConfiguration servers,
                                                boolean missingIsOk,
                                                ServerAction serverAction) {
        return doAcrossServers(servers, SimpleSlingSupportFactory.INSTANCE, missingIsOk, serverAction)
    }

    /**
     * Runs the given server action across all the provided active servers
     *
     * @param servers the collection of servers to run the action across
     * @param slingSupportFactory the factory for creating the connection helper
     * @param missingIsOk is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    private static HttpResponse doAcrossServers(SlingServersConfiguration servers,
                                                SlingSupportFactory slingSupportFactory,
                                                boolean missingIsOk,
                                                ServerAction serverAction) {
        def httpResponse = new HttpResponse(HTTP_OK, '')

        Iterator<SlingServerConfiguration> activeServers = servers.iterator()
        while (activeServers.hasNext() && !isBadResponse(httpResponse.code, missingIsOk)) {
            def serverConfig = activeServers.next()
            def slingSupport = slingSupportFactory.create(serverConfig)
            def resp = serverAction.run(slingSupport)

            httpResponse = and(httpResponse, resp, missingIsOk)
        }
        return httpResponse
    }

    /**
     * Does the given http code indicate there was an error?
     * <p>
     * Good codes are 200 - 399. However, 408 (client timeout) is also not considered to be an error.
     *
     * @param respCode the code to check if it indicates an error or not
     * @param missingIsOk is a 404 (missing) acceptable?
     */
    static boolean isBadResponse(int respCode, boolean missingIsOk) {
        if (respCode == HTTP_NOT_FOUND) return !missingIsOk

        if (respCode >= HTTP_OK) {
            if (respCode < HTTP_BAD_REQUEST) return false
            if (respCode == HTTP_CLIENT_TIMEOUT) return false
            return true
        }
        return true
    }

    /**
     * For the server pointed to by "slingSupport" this asks the server for all of its bundles. For every bundle that
     * is RESOLVED, this will call "start" on it.
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    static HttpResponse startInactiveBundles(@Nonnull SlingSupport slingSupport) {
        def serverConf = slingSupport.serverConf
        def resp = slingSupport.doGet(getBundlesControlUri(serverConf))

        if (resp.code == HTTP_OK) {
            Map json = new JsonSlurper().parseText(resp.body) as Map
            List<Map> data = json.data as List

            def inactiveBundles = data.
                findAll { it.state == RESOLVED.stateString }.
                collect { it.symbolicName } as List<String>

            HttpResponse httpResponse = startBundles(inactiveBundles, slingSupport)
            return httpResponse
        }
        return resp
    }


    private static HttpResponse startBundles(List<String> inactiveBundles, SlingSupport slingSupport) {
        def serverConf = slingSupport.serverConf
        def bundleServerConfiguration = new BundleServerConfiguration(serverConf)
        def httpResponse = new HttpResponse(HTTP_OK, '')
        def inactiveBundlesIter = inactiveBundles.iterator()

        while (inactiveBundlesIter.hasNext() && !isBadResponse(httpResponse.code, false)) {
            String symbolicName = inactiveBundlesIter.next()
            def bundleConfiguration = new SlingBundleConfiguration(symbolicName, "")
            def slingBundleSupport = new SlingBundleSupport(bundleConfiguration, bundleServerConfiguration, slingSupport)
            log.info "Trying to start inactive bundle: ${symbolicName}"
            def startResp = slingBundleSupport.startBundle()
            httpResponse = and(httpResponse, startResp, false)
        }
        return httpResponse
    }

    /**
     * Returns the URL to use to do actions on bundles.
     */
    @Nonnull
    static URI getBundlesControlUri(SlingServerConfiguration serverConf) {
        URI base = serverConf.baseUri
        new URI(base.scheme, base.userInfo, base.host, base.port, "${BundleServerConfiguration.BUNDLE_CONTROL_BASE_PATH}.json", null, null)
    }

    /**
     * Calls {@link CqPackageHelper#validateAllBundles(List, SlingSupport)} for
     * each server in {@link SlingServersConfiguration} and all the bundles in the configuration
     *
     * @param configuration the Gradle Configuration such as "compile" to retrieve the list of bundles from
     *
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    HttpResponse validateBundles(GradleClasspathConfiguration configuration) {
        def resolvedConfiguration = configuration.resolvedConfiguration
        def symbolicNamesList = buildSymbolicNamesList(resolvedConfiguration)
        return doAcrossServers(false) { SlingSupport slingSupport ->
            return validateAllBundles(symbolicNamesList, slingSupport)
        }
    }

    /**
     * Calls {@link CqPackageHelper#validateAllBundles(List, SlingSupport)} for
     * each server in {@link SlingServersConfiguration} and all the bundles in the package file downloaded from
     * that server
     *
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    HttpResponse validateRemoteBundles() {
        return doAcrossServers(false) { SlingSupport slingSupport ->
            def packageServerConf = new PackageServerConfiguration(slingSupport.serverConf)
            def packageSupport = new SlingPackageSupport(packageServerConf, slingSupport)
            def namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(packageSupport)
            return validateAllBundles(namesFromDownloadedPackage, slingSupport)
        }
    }

    /**
     * Calls each server in {@link SlingServersConfiguration} and all the bundles in 'symbolicNames' to verify that
     * they are not in non-ACTIVE states. If there are, it returns HTTP_INTERNAL_ERROR (500).
     *
     * @param symbolicNames the symbolic names to check; if null then all bundles on the server are checked
     * @param slingSupport the server association to user
     *
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an
     * empty HTTP_OK; otherwise returns the first error response it came across
     */
    @Nonnull
    @SuppressWarnings("GroovyPointlessBoolean")
    static HttpResponse validateAllBundles(@Nullable Collection<String> symbolicNames,
                                           @Nonnull SlingSupport slingSupport) {
        def serverConf = slingSupport.serverConf
        def serverName = serverConf.name
        log.info "Checking for NON-ACTIVE bundles on ${serverName}"

        final pollingTxt = new DotPrinter()
        boolean bundlesActive = false

        HttpResponse theResp = new HttpResponse(HTTP_OK, "")

        block(
            serverConf.maxWaitMs,
            { serverConf.active && bundlesActive == false && theResp.code == HTTP_OK },
            {
                log.info pollingTxt.increment()

                def resp = slingSupport.doGet(getBundlesControlUri(serverConf))
                if (resp.code == HTTP_OK) {
                    bundlesActive = bundlesAreActive(symbolicNames, resp.body)
                }
                else {
                    if (resp.code == HTTP_CLIENT_TIMEOUT)
                        serverConf.active = false
                    theResp = resp
                }
            },
            serverConf.retryWaitMs
        )

        if (serverConf.active == false) return new HttpResponse(HTTP_CLIENT_TIMEOUT, serverName)

        if (theResp.code != HTTP_OK) return theResp

        if (bundlesActive == false) {
            if (symbolicNames == null)
                return new HttpResponse(HTTP_INTERNAL_ERROR, "Not all bundles are ACTIVE on ${serverName}")
            else
                return new HttpResponse(HTTP_INTERNAL_ERROR, "Not all bundles for ${symbolicNames} are ACTIVE on ${serverName}")
        }
        else {
            log.info("Bundles are ACTIVE on ${serverName}")
            return theResp
        }
    }


    private static bundlesAreActive(@Nullable Collection<String> symbolicNames, String body) {
        try {
            def json = new JsonSlurper().parseText(body) as Map
            List<Map<String, Object>> data = json.data as List

            List<Map<String, Object>> allBundles
            if (symbolicNames != null) {
                def knownBundles = data.findAll { Map b -> symbolicNames.contains(b.symbolicName) }
                def knownBundleNames = knownBundles.collect { Map b -> (String)b.symbolicName }
                def missingBundleNames = (symbolicNames - knownBundleNames)
                def missingBundles = missingBundleNames.collect { String name ->
                    [symbolicName: name, state: MISSING.stateString] as Map<String, Object>
                }
                allBundles = knownBundles + missingBundles
            }
            else {
                allBundles = data
            }

            if (!hasAnInactiveBundle(allBundles)) {
                if (log.debugEnabled) allBundles.each { Map b -> log.debug "Active bundle: ${b.symbolicName}" }
                return true
            }
            return false
        }
        catch (Exception exp) {
            throw new GradleException("Problem parsing \"${body}\"", exp)
        }
    }


    private static boolean hasAnInactiveBundle(final Collection<Map<String, Object>> knownBundles) {
        final activeBundles = knownBundles.findAll { bundle ->
            bundle.state == ACTIVE.stateString ||
                bundle.state == FRAGMENT.stateString
        } as Collection<Map>

        final inactiveBundles = inactiveBundles(knownBundles)

        if (log.infoEnabled) inactiveBundles.each { log.info("bundle ${it.symbolicName} NOT active: ${it.state}") }
        if (log.debugEnabled) activeBundles.each { log.debug("bundle ${it.symbolicName} IS active") }

        return inactiveBundles.size() > 0
    }

    /**
     * Calls {@link CqPackageHelper#uninstallAllBundles(List, SlingSupport, UninstallBundlePredicate)} for
     * each server in {@link SlingServersConfiguration} and all the bundles in the package file downloaded from
     * that server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or a
     * 404 (not installed) or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    HttpResponse uninstallBundles(UninstallBundlePredicate bundlePredicate) {
        return doAcrossServers(true) { SlingSupport slingSupport ->
            def serverConf = slingSupport.serverConf
            def packageServerConfiguration = new PackageServerConfiguration(serverConf)
            def slingPackageSupport = new SlingPackageSupport(packageServerConfiguration, slingSupport)
            def packageInfo = RuntimePackageProperties.packageProperties(slingPackageSupport, PackageId.fromString(packageName))
            if (packageInfo.succeeded()) { // package is installed
                def namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(slingPackageSupport)
                return uninstallAllBundles(namesFromDownloadedPackage, slingSupport, bundlePredicate)
            }
            else {
                log.info("${packageName} is not on ${serverConf.name}")
                return new HttpResponse(HTTP_OK, '')
            }
        }
    }

    /**
     * Given a list of symbolic names on a server, uninstalls them if they match the predicate
     *
     * @param symbolicNames the symbolic names on a server to check against
     * @param slingSupport the SlingSupport for a particular server
     * @param predicate the predicate determine if the bundle should be uninstalled
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or a
     * 404 (not installed) or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    @Nonnull
    HttpResponse uninstallAllBundles(@Nonnull List<String> symbolicNames,
                                     @Nonnull SlingSupport slingSupport,
                                     @Nullable UninstallBundlePredicate predicate) {
        log.info "Uninstalling/removing bundles on ${slingSupport.serverConf.name}: ${symbolicNames}"

        def httpResponse = new HttpResponse(HTTP_OK, '')

        Iterator<String> symbolicNameIter = symbolicNames.iterator()

        while (symbolicNameIter.hasNext() && !isBadResponse(httpResponse.code, true)) {
            def symbolicName = symbolicNameIter.next()
            def bundleConfiguration = new SlingBundleConfiguration(symbolicName, "")
            def slingBundleSupport = new SlingBundleSupport(bundleConfiguration, new BundleServerConfiguration(slingSupport.serverConf), slingSupport)
            if (predicate != null && predicate.eval(symbolicName)) {
                log.info "Stopping $symbolicName on ${slingSupport.serverConf.name}"
                def stopResp = slingBundleSupport.stopBundle()
                httpResponse = and(httpResponse, stopResp, true)
                if (!isBadResponse(httpResponse.code, true)) {
                    log.info "Uninstalling $symbolicName on ${slingSupport.serverConf.name}"
                    def uninstallResp = slingBundleSupport.uninstallBundle()
                    httpResponse = and(httpResponse, uninstallResp, true)
                }
            }
        }

        return httpResponse
    }

    // TODO: Move to be in HttpResponse
    static HttpResponse and(HttpResponse first, HttpResponse second, boolean missingIsOk) {
        if (first == null && second == null) return new HttpResponse(HTTP_OK, '')
        if (first == null) {
            return second
        }
        else if (second == null) {
            return first
        }

        // TIMEOUT is effectively a "not set"
        if (first.code == HTTP_CLIENT_TIMEOUT) return second

        // once first is not OK, everything from that point is not OK
        if (isBadResponse(first.code, missingIsOk)) return first

        // TIMEOUT is effectively a "not set"
        if (second.code == HTTP_CLIENT_TIMEOUT) return first

        return second
    }


    private static Collection<Map> inactiveBundles(Collection<Map> knownBundles) {
        return knownBundles.findAll { bundle ->
            bundle.state == INSTALLED.stateString ||
                bundle.state == RESOLVED.stateString ||
                bundle.state == MISSING.stateString
        } as Collection<Map>
    }

    /**
     * Downloads this package from the server contained in "slingPackageSupport", then extracts the bundles it contains
     * and returns the list of symbolic names for those bundles
     *
     * @param slingPackageSupport the package/server combination to get the package file from
     */
    @Nonnull
    private List<String> symbolicNamesFromDownloadedPackage(SlingPackageSupport slingPackageSupport) {
        try {
            File downloadDir = new File("${project.buildDir}/tmp")
            downloadDir.mkdirs()
            def packageInfoSF = RuntimePackageProperties.packageProperties(slingPackageSupport, PackageId.fromString(packageName))
            if (packageInfoSF.failed()) throw new IllegalStateException("Could not get package information: ${packageInfoSF.error}")
            def packageInfo = packageInfoSF.value
            log.info "Download filename ${packageInfo.downloadName}"
            String packageFilename = "${downloadDir}/${packageInfo.downloadName}"
            final path = packageInfo.path

            def packageServerConf = slingPackageSupport.packageServerConf
            final zipUri = URI.create("${packageServerConf.packageDownloadUri}?_charset_=utf-8&path=${path}")

            log.info("Filename from package list: ${packageFilename}")
            log.info("Filepath from package list: ${path}")
            log.info("Zip URI from package list: ${zipUri}")

            def file = downloadFile(packageFilename, zipUri, packageServerConf.serverConf)
            def zipFile = new ZipFile(file)
            def zipEntries = zipFile.entries().findAll { ZipEntry entry -> entry.name.endsWith(".jar") } as List

            def symbolicNames = []
            zipEntries.each {
                def filename = it.toString()
                def actualFileName = filename.split("/").last()
                def entry = zipFile.getEntry(filename)
                log.debug("Unzipping to ${project.buildDir}/tmp/${actualFileName}...")
                def is = zipFile.getInputStream(entry)
                def jarFile = new File("${project.buildDir}/tmp/${actualFileName}")
                def out = new FileOutputStream(jarFile)

                try {
                    IOUtils.copy(is, out)
                }
                finally {
                    is.close()
                    out.close()
                }

                def bundleSymbolicName = getSymbolicName(jarFile)
                if (bundleSymbolicName != null) {
                    symbolicNames.add(bundleSymbolicName)
                }
                else {
                    log.warn "${file} contains a non-OSGi jar file: ${jarFile}"
                }
                log.debug("Cleaning up. Deleting $jarFile...")
                jarFile.delete()
            }
            log.debug("Cleaning up. Deleting $file")
            file.delete()
            log.info("Bundles from downloaded zipfile: $symbolicNames")
            return symbolicNames
        }
        catch (Exception exp) {
            log.error "There was a problem getting symbolic names from downloaded package \"${slingPackageSupport.packageServerConf}\""
            throw exp
        }
    }


    @Nonnull
    private File downloadFile(String filename, URI uri, SlingServerConfiguration serverConfig) {
        HttpGet httpGet = new HttpGet(uri)
        httpGet.addHeader(BasicScheme.authenticate(
            new UsernamePasswordCredentials(serverConfig.username, serverConfig.password), "UTF-8", false))

        HttpClient client = new DefaultHttpClient()
        org.apache.http.HttpResponse httpResponse = client.execute(httpGet)
        InputStream is = httpResponse.entity.content
        File file = new File(filename)
        OutputStream out = new FileOutputStream(file)

        try {
            IOUtils.copy(is, out)
        }
        finally {
            is.close()
            out.close()
        }

        return file
    }

    /**
     * For the given "resolved configuration" (all of the artifacts in a Gradle Configuration, such as "compile"),
     * return all of the bundle symbolic names in the bundles.
     */
    @Nonnull
    private List buildSymbolicNamesList(ResolvedConfiguration resolvedConfiguration) {
        return resolvedConfiguration.resolvedArtifacts.collect { ra ->
            getSymbolicName(ra.file)
        }
    }

    /**
     * Get the OSGi bundle symbolic name from the file's metadata.
     * @return null if the file is not an OSGi bundle
     */
    @Nullable
    static String getSymbolicName(File file) {
        try {
            JarFile jar = new JarFile(file)
            Manifest manifest = jar.manifest
            final entries = manifest.mainAttributes
            return entries.getValue('Bundle-SymbolicName') as String
        }
        catch (ZipException exp) {
            throw new IllegalStateException("Trying to open '${file}'", exp)
        }
    }

    /**
     * Does the given JAR file have basic OSGi metadata? (Specifically "Bundle-SymbolicName")
     */
    static boolean isOsgiFile(File file) {
        return getSymbolicName(file) != null
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    @TypeChecked
    public interface ServerAction {
        HttpResponse run(@Nonnull SlingSupport slingSupport);
    }

    /**
     * Functional interface for {@link #uninstallAllBundles(List, SlingSupport, UninstallBundlePredicate)}
     */
    static interface UninstallBundlePredicate {
        /**
         * Returns true if the symbolic name passed in should be uninstalled; otherwise false
         */
        boolean eval(String symbolicName)
    }

    @TypeChecked
    static class DotPrinter {
        private final StringBuilder str = new StringBuilder()


        String increment() {
            str.append('.' as char).toString()
        }
    }

}
