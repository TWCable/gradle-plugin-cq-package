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

import com.twcable.gradle.http.HttpResponse;
import com.twcable.gradle.sling.SimpleSlingSupportFactory;
import com.twcable.gradle.sling.SlingServerConfiguration;
import com.twcable.gradle.sling.SlingServersConfiguration;
import com.twcable.gradle.sling.SlingSupport;
import com.twcable.gradle.sling.SlingSupportFactory;
import com.twcable.gradle.sling.osgi.BundleServerConfiguration;
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration;
import com.twcable.gradle.sling.osgi.SlingBundleSupport;
import groovy.json.JsonSlurper;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.file.FileCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static com.twcable.gradle.sling.osgi.BundleState.ACTIVE;
import static com.twcable.gradle.sling.osgi.BundleState.FRAGMENT;
import static com.twcable.gradle.sling.osgi.BundleState.INSTALLED;
import static com.twcable.gradle.sling.osgi.BundleState.MISSING;
import static com.twcable.gradle.sling.osgi.BundleState.RESOLVED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Has external dependencies on {@link SlingServersConfiguration}
 */
@SuppressWarnings({"Convert2MethodRef", "WeakerAccess", "RedundantTypeArguments", "RedundantCast"})
public class CqPackageHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CqPackageHelper.class);

    public static final String NAME = "cqPkgHelper";
    private final Project project;
    private PackageManager packageManager = new PackageManagerImpl();


    public CqPackageHelper(Project project) {
        if (project == null) throw new GradleException("project == null");
        this.project = project;
    }


    private SlingServersConfiguration slingServersConfiguration() {
        return project.getExtensions().getByType(SlingServersConfiguration.class);
    }


    /**
     * Returns the package name. Uses the project's name.
     */
    public String getPackageName() {
        return project.getName();
    }


    public void installPackage(SlingPackageSupportFactory factory) {
        InstallPackage.install(getPackageName(), slingServersConfiguration(), factory);
    }


    public void uninstallPackage(SlingPackageSupportFactory factory) {
        UninstallPackage.uninstall(getPackageName(), slingServersConfiguration(), factory);
    }


    public void deletePackage(SlingPackageSupportFactory factory) {
        DeletePackage.delete(getPackageName(), slingServersConfiguration(), factory);
    }


    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     *
     * @return null only if the server timed out (in which case "active" is disabled on the passed serverConfig)
     */
    public @Nullable Collection<RuntimePackageProperties> listPackages(SlingPackageSupport slingPackageSupport) {
        SlingServerConfiguration serverConf = slingPackageSupport.getPackageServerConf().serverConf;
        if (!serverConf.getActive()) return null;

        final SuccessOrFailure<Collection<RuntimePackageProperties>> packagesSF = ListPackages.listPackages(slingPackageSupport);
        if (packagesSF.getError() != null) {
            if (Status.SERVER_TIMEOUT.equals(packagesSF.getError())) {
                serverConf.setActive(false);
                return null;
            }
            else {
                throw new GradleException("Unknown status from listing packages: " + packagesSF.getError());
            }
        }

        return packagesSF.getValue();
    }


    /**
     * Uploads the Package for the current Project to all the servers.
     *
     * @param factory strategy for creating SlingPackageSupport instances
     * @return the "aggregated" status: {@link Status#OK}, {@link PackageStatus#UNRESOLVED_DEPENDENCIES} or {@link PackageStatus#NO_PACKAGE}
     */
    @SuppressWarnings("ConstantConditions")
    public Status uploadPackage(SlingPackageSupportFactory factory) {
        if (factory == null) throw new IllegalArgumentException("factory == null");
        File sourceFile = UploadPackage.getThePackageFile(project);

        final SlingServersConfiguration slingServersConfiguration = slingServersConfiguration();

        Status status = PackageStatus.OK;
        Iterator<SlingServerConfiguration> serversIter = slingServersConfiguration.iterator();
        while (serversIter.hasNext() && status.equals(Status.OK)) {
            SlingServerConfiguration serverConfig = serversIter.next();
            Status uploadStatus = UploadPackage.upload(sourceFile, false, factory.create(serverConfig), packageManager);
            if (uploadStatus.equals(PackageStatus.UNRESOLVED_DEPENDENCIES) || uploadStatus.equals(PackageStatus.NO_PACKAGE))
                status = uploadStatus;
        }

        return status;
    }


    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @throws IOException if it can't read the file
     */
    public static PackageProperties packageProperties(File packageFile) throws IOException {
        final PackageManagerImpl packageManager = new PackageManagerImpl();
        final VaultPackage vltPck = packageManager.open(packageFile);

        return vltPck.getProperties();
    }


    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @throws IOException if it can't read the file
     * @see #packageProperties(File)
     */
    public static PackageId packageId(File packageFile) throws IOException {
        return packageProperties(packageFile).getId();
    }


    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @throws IOException if it can't read the file
     * @see #packageId(File)
     */
    public static String packageName(File packageFile) throws IOException {
        return packageId(packageFile).getName();
    }


    /**
     * Calls {@link CqPackageHelper#startInactiveBundles(SlingSupport)} for
     * each server in {@link SlingServersConfiguration}
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    public HttpResponse startInactiveBundles() {
        return doAcrossServers(false, slingSupport -> startInactiveBundles(slingSupport));
    }


    /**
     * Runs the given server action across all the active servers
     *
     * @param missingIsOk  is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    private HttpResponse doAcrossServers(boolean missingIsOk, ServerAction serverAction) {
        return doAcrossServers(slingServersConfiguration(), missingIsOk, serverAction);
    }


    /**
     * Runs the given server action across all the provided active servers
     *
     * @param servers      the collection of servers to run the action across
     * @param missingIsOk  is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    private static HttpResponse doAcrossServers(SlingServersConfiguration servers, boolean missingIsOk, ServerAction serverAction) {
        return doAcrossServers(servers, SimpleSlingSupportFactory.INSTANCE, missingIsOk, serverAction);
    }


    /**
     * Runs the given server action across all the provided active servers
     *
     * @param servers             the collection of servers to run the action across
     * @param slingSupportFactory the factory for creating the connection helper
     * @param missingIsOk         is a 404 response considered OK? If false, it counts as an error
     * @param serverAction        the action to run against the bundle on the server
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    private static HttpResponse doAcrossServers(SlingServersConfiguration servers, SlingSupportFactory slingSupportFactory, boolean missingIsOk, ServerAction serverAction) {
        HttpResponse httpResponse = new HttpResponse(HTTP_OK, "");

        Iterator<SlingServerConfiguration> activeServers = servers.iterator();
        while (activeServers.hasNext() && !isBadResponse(httpResponse.getCode(), missingIsOk)) {
            SlingServerConfiguration serverConfig = activeServers.next();
            SlingSupport slingSupport = slingSupportFactory.create(serverConfig);
            HttpResponse resp = serverAction.run(slingSupport);

            httpResponse = and(httpResponse, resp, missingIsOk);
        }

        return httpResponse;
    }


    /**
     * Does the given http code indicate there was an error?
     * <p>
     * Good codes are 200 - 399. However, 408 (client timeout) is also not considered to be an error.
     *
     * @param respCode    the code to check if it indicates an error or not
     * @param missingIsOk is a 404 (missing) acceptable?
     */
    @SuppressWarnings("RedundantIfStatement")
    public static boolean isBadResponse(int respCode, boolean missingIsOk) {
        if (respCode == HTTP_NOT_FOUND) return !missingIsOk;

        if (respCode >= HTTP_OK) {
            if (respCode < HTTP_BAD_REQUEST) return false;
            if (respCode == HTTP_CLIENT_TIMEOUT) return false;
            return true;
        }

        return true;
    }


    /**
     * For the server pointed to by "slingSupport" this asks the server for all of its bundles. For every bundle that
     * is RESOLVED, this will call "start" on it.
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @SuppressWarnings("unchecked")
    public static HttpResponse startInactiveBundles(SlingSupport slingSupport) {
        SlingServerConfiguration serverConf = slingSupport.getServerConf();
        HttpResponse resp = slingSupport.doGet(getBundlesControlUri(serverConf));

        if (resp.getCode() == HTTP_OK) {
            Map json = (Map<String, Object>)new JsonSlurper().parseText(resp.getBody());
            List<Map<String, Object>> data = (@NonNull List<Map<String, Object>>)json.get("data");

            final List<String> inactiveBundles = data.stream().
                filter(it -> Objects.equals((@NonNull String)it.get("state"), RESOLVED.getStateString())).
                map(it -> (@NonNull String)it.get("symbolicName")).
                collect(Collectors.<String>toList());

            return startBundles(inactiveBundles, slingSupport);
        }

        return resp;
    }


    private static HttpResponse startBundles(List<String> inactiveBundles, SlingSupport slingSupport) {
        SlingServerConfiguration serverConf = slingSupport.getServerConf();
        BundleServerConfiguration bundleServerConfiguration = new BundleServerConfiguration(serverConf);
        HttpResponse httpResponse = new HttpResponse(HTTP_OK, "");
        Iterator<String> inactiveBundlesIter = inactiveBundles.iterator();

        while (inactiveBundlesIter.hasNext() && !isBadResponse(httpResponse.getCode(), false)) {
            final String symbolicName = inactiveBundlesIter.next();
            SlingBundleConfiguration bundleConfiguration = new SlingBundleConfiguration(symbolicName, "");
            SlingBundleSupport slingBundleSupport = new SlingBundleSupport(bundleConfiguration, bundleServerConfiguration, slingSupport);
            LOG.info("Trying to start inactive bundle: " + symbolicName);
            HttpResponse startResp = slingBundleSupport.startBundle();
            httpResponse = and(httpResponse, startResp, false);
        }

        return httpResponse;
    }


    /**
     * Returns the URL to use to do actions on bundles.
     */
    @SuppressWarnings("argument.type.incompatible")
    public static URI getBundlesControlUri(SlingServerConfiguration serverConf) {
        final URI base = serverConf.getBaseUri();
        try {
            return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), BundleServerConfiguration.getBUNDLE_CONTROL_BASE_PATH() + ".json", null, null);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not create bundle control URI from " + serverConf);
        }
    }


    /**
     * Calls {@link CqPackageHelper#validateAllBundles(Collection, SlingSupport)} for
     * each server in {@link SlingServersConfiguration} and all the bundles in the configuration
     *
     * @param configuration the Gradle Configuration such as "compile" to retrieve the list of bundles from
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    public HttpResponse validateBundles(Configuration configuration) {
        ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
        final List<String> symbolicNamesList = symbolicNames(resolvedConfiguration);
        return doAcrossServers(false, slingSupport -> validateAllBundles(symbolicNamesList, slingSupport));
    }


    /**
     * Calls {@link CqPackageHelper#validateAllBundles(Collection, SlingSupport)} for
     * each server in {@link SlingServersConfiguration} and all the bundles
     *
     * @param files the list of bundles
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    public HttpResponse validateBundles(FileCollection files) {
        final List<String> symbolicNamesList = symbolicNames(files);
        return doAcrossServers(false, slingSupport -> validateAllBundles(symbolicNamesList, slingSupport));
    }


    /**
     * For the given "resolved configuration" (all of the artifacts in a Gradle Configuration, such as "compile"),
     * return all of the bundle symbolic names in the bundles.
     */
    private List<String> symbolicNames(ResolvedConfiguration resolvedConfiguration) {
        return symbolicNames(project.files(resolvedConfiguration.getResolvedArtifacts()));
    }


    /**
     * For the given set of files, return all of the bundle symbolic names in the bundles.
     */
    private static List<String> symbolicNames(Iterable<File> files) {
        return StreamSupport.stream(files.spliterator(), false).
            flatMap(file -> {
                try {
                    @Nullable final String symbolicName = getSymbolicName(file);
                    if (symbolicName == null) return Stream.<String>empty();
                    else return Stream.of(symbolicName);
                }
                catch (IOException e) {
                    throw new IllegalStateException("Problem getting symbolic names from " + file, e);
                }
            }).
            collect(Collectors.<String>toList());
    }


    /**
     * Calls {@link CqPackageHelper#validateAllBundles(Collection, SlingSupport)} for
     * each server in {@link SlingServersConfiguration} and all the bundles in the package file downloaded from
     * that server
     *
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    public HttpResponse validateRemoteBundles() {
        return doAcrossServers(false, slingSupport -> {
            PackageServerConfiguration packageServerConf = new PackageServerConfiguration(slingSupport.getServerConf());
            SlingPackageSupport packageSupport = new SlingPackageSupport(packageServerConf, slingSupport);
            try {
                List<String> namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(packageSupport);
                return validateAllBundles(namesFromDownloadedPackage, slingSupport);
            }
            catch (URISyntaxException | IOException e) {
                throw new IllegalStateException("Problem validating bundles: " + packageSupport, e);
            }
        });
    }


    /**
     * Calls each server in {@link SlingServersConfiguration} and all the bundles in 'symbolicNames' to verify that
     * they are not in non-ACTIVE states. If there are, it returns HTTP_INTERNAL_ERROR (500).
     *
     * @param symbolicNames the symbolic names to check; if null then all bundles on the server are checked
     * @param slingSupport  the server association to user
     * @return HTTP_INTERNAL_ERROR if there are inactive bundles, otherwise the "aggregate" HTTP response: if all
     * the calls are in the >= 200 and <400 range, or a 408 (timeout, server not running) the returns an
     * empty HTTP_OK; otherwise returns the first error response it came across
     */
    @SuppressWarnings("PointlessBooleanExpression")
    public static HttpResponse validateAllBundles(@Nullable final Collection<String> symbolicNames, final SlingSupport slingSupport) {
        final SlingServerConfiguration serverConf = slingSupport.getServerConf();
        final String serverName = serverConf.getName();
        LOG.info("Checking for NON-ACTIVE bundles on " + serverName);

        final DotPrinter pollingTxt = new DotPrinter();
        final boolean[] bundlesActive = new boolean[]{false};
        final HttpResponse[] theResp = new HttpResponse[]{new HttpResponse(HTTP_OK, "")};

        SlingSupport.block(serverConf.getMaxWaitMs(),
            () -> serverConf.getActive() && bundlesActive[0] == false && theResp[0].getCode() == HTTP_OK,
            () -> {
                LOG.info(pollingTxt.increment());

                HttpResponse resp = slingSupport.doGet(getBundlesControlUri(serverConf));
                if (resp.getCode() == HTTP_OK) {
                    bundlesActive[0] = bundlesAreActive(symbolicNames, resp.getBody());
                }
                else {
                    if (resp.getCode() == HTTP_CLIENT_TIMEOUT) serverConf.setActive(false);
                    theResp[0] = resp;
                }
            },
            serverConf.getRetryWaitMs());

        if (serverConf.getActive() == false) return new HttpResponse(HTTP_CLIENT_TIMEOUT, serverName);

        if (theResp[0].getCode() != HTTP_OK) return theResp[0];

        if (bundlesActive[0] == false) {
            if (symbolicNames == null)
                return new HttpResponse(HTTP_INTERNAL_ERROR, "Not all bundles are ACTIVE on " + serverName);
            else
                return new HttpResponse(HTTP_INTERNAL_ERROR, "Not all bundles for " + symbolicNames + " are ACTIVE on " + serverName);
        }
        else {
            LOG.info("Bundles are ACTIVE on " + serverName);
            return theResp[0];
        }
    }


    @SuppressWarnings({"unchecked", "RedundantCast"})
    private static Boolean bundlesAreActive(@Nullable final Collection<String> symbolicNames, final String body) {
        try {
            Map<String, Object> json = (Map<String, Object>)new JsonSlurper().parseText(body);
            List<Map<String, Object>> data = (@NonNull List<Map<String, Object>>)json.get("data");

            List<Map<String, Object>> allBundles;
            if (symbolicNames != null) {
                allBundles = bundlesAreActive(data, symbolicNames);
            }
            else {
                allBundles = data;
            }

            if (!hasAnInactiveBundle(allBundles)) {
                if (LOG.isDebugEnabled())
                    allBundles.forEach(b -> LOG.debug("Active bundle: " + b.get("symbolicName")));
                return true;
            }

            return false;
        }
        catch (Exception exp) {
            throw new GradleException("Problem parsing \"" + body + "\"", exp);
        }

    }


    private static List<Map<String, Object>> bundlesAreActive(List<Map<String, Object>> data, Collection<String> symbolicNames) {
        val knownBundles = data.stream().
            filter(b -> symbolicNames.contains((@NonNull String)b.get("symbolicName"))).
            collect(Collectors.<Map<String, Object>>toList());
        val knownBundleNames = knownBundles.stream().
            map(b -> (@NonNull String)b.get("symbolicName")).
            collect(Collectors.<String>toList());

        val missingBundleNames = new ArrayList<String>(symbolicNames);
        missingBundleNames.removeAll(knownBundleNames);

        val missingBundles = missingBundleNames.stream().map(name -> {
            Map<String, Object> bundle = new HashMap<>();
            bundle.put("symbolicName", name);
            bundle.put("state", MISSING.getStateString());
            return bundle;
        }).collect(Collectors.<Map<String, Object>>toList());

        val allBundles = new ArrayList<Map<String, Object>>(knownBundles);
        allBundles.addAll(missingBundles);
        return allBundles;
    }


    private static boolean hasAnInactiveBundle(final Collection<Map<String, Object>> knownBundles) {
        final List<Map<String, Object>> activeBundles = knownBundles.stream().
            filter(bundle -> {
                    val state = (@NonNull String)bundle.get("state");
                    return state.equals(ACTIVE.getStateString()) || state.equals(FRAGMENT.getStateString());
                }
            ).collect(Collectors.<Map<String, Object>>toList());

        final Collection<Map<String, Object>> inactiveBundles = inactiveBundles(knownBundles);

        if (LOG.isInfoEnabled()) inactiveBundles.forEach(it ->
            LOG.info("bundle " + it.get("symbolicName") + " NOT active: " + it.get("state"))
        );
        if (LOG.isDebugEnabled()) activeBundles.forEach(it ->
            LOG.debug("bundle " + it.get("symbolicName") + " IS active")
        );

        return !inactiveBundles.isEmpty();
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
    public HttpResponse uninstallBundles(final UninstallBundlePredicate bundlePredicate) {
        return doAcrossServers(true, slingSupport -> {
            final SlingServerConfiguration serverConf = slingSupport.getServerConf();
            PackageServerConfiguration packageServerConfiguration = new PackageServerConfiguration(serverConf);
            SlingPackageSupport slingPackageSupport = new SlingPackageSupport(packageServerConfiguration, slingSupport);
            SuccessOrFailure<RuntimePackageProperties> packageInfo = RuntimePackageProperties.packageProperties(slingPackageSupport, PackageId.fromString(getPackageName()));
            if (packageInfo.succeeded()) { // package is installed
                try {
                    List<String> namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(slingPackageSupport);
                    return uninstallAllBundles(namesFromDownloadedPackage, slingSupport, bundlePredicate);
                }
                catch (URISyntaxException | IOException e) {
                    throw new IllegalStateException("Problem getting symbolic names from " + packageInfo.getValue().getDownloadName(), e);
                }
            }
            else {
                LOG.info(getPackageName() + " is not on " + serverConf.getName());
                return new HttpResponse(HTTP_OK, "");
            }
        });
    }


    /**
     * Given a list of symbolic names on a server, uninstalls them if they match the predicate
     *
     * @param symbolicNames the symbolic names on a server to check against
     * @param slingSupport  the SlingSupport for a particular server
     * @param predicate     the predicate determine if the bundle should be uninstalled
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or a
     * 404 (not installed) or a 408 (timeout, server not running) the returns an empty HTTP_OK;
     * otherwise returns the first error response it came across
     */
    public HttpResponse uninstallAllBundles(final List<String> symbolicNames, final SlingSupport slingSupport, @Nullable UninstallBundlePredicate predicate) {
        LOG.info("Uninstalling/removing bundles on " + slingSupport.getServerConf().getName() + ": " + symbolicNames);

        HttpResponse httpResponse = new HttpResponse(HTTP_OK, "");

        Iterator<String> symbolicNameIter = symbolicNames.iterator();

        while (symbolicNameIter.hasNext() && !isBadResponse(httpResponse.getCode(), true)) {
            String symbolicName = symbolicNameIter.next();
            SlingBundleConfiguration bundleConfiguration = new SlingBundleConfiguration(symbolicName, "");
            SlingBundleSupport slingBundleSupport = new SlingBundleSupport(bundleConfiguration, new BundleServerConfiguration(slingSupport.getServerConf()), slingSupport);
            if (predicate != null && predicate.eval(symbolicName)) {
                LOG.info("Stopping " + symbolicName + " on " + slingSupport.getServerConf().getName());
                HttpResponse stopResp = slingBundleSupport.stopBundle();
                httpResponse = and(httpResponse, stopResp, true);
                if (!isBadResponse(httpResponse.getCode(), true)) {
                    LOG.info("Uninstalling " + symbolicName + " on " + slingSupport.getServerConf().getName());
                    HttpResponse uninstallResp = slingBundleSupport.uninstallBundle();
                    httpResponse = and(httpResponse, uninstallResp, true);
                }

            }

        }

        return httpResponse;
    }


    public static HttpResponse and(HttpResponse first, HttpResponse second, boolean missingIsOk) {
        if (first == null && second == null) return new HttpResponse(HTTP_OK, "");
        if (first == null) {
            return second;
        }
        else if (second == null) {
            return first;
        }

        // TIMEOUT is effectively a "not set"
        if (first.getCode() == HTTP_CLIENT_TIMEOUT) return second;

        // once first is not OK, everything from that point is not OK
        if (isBadResponse(first.getCode(), missingIsOk)) return first;

        // TIMEOUT is effectively a "not set"
        if (second.getCode() == HTTP_CLIENT_TIMEOUT) return first;

        return second;
    }


    private static Collection<Map<String, Object>> inactiveBundles(Collection<Map<String, Object>> knownBundles) {
        return knownBundles.stream().filter(bundle -> {
            val state = (@NonNull String)bundle.get("state");
            return state.equals(INSTALLED.getStateString()) ||
                state.equals(RESOLVED.getStateString()) ||
                state.equals(MISSING.getStateString());
        }).collect(Collectors.<Map<String, Object>>toList());
    }


    /**
     * Downloads this package from the server contained in "slingPackageSupport", then extracts the bundles it contains
     * and returns the list of symbolic names for those bundles
     *
     * @param slingPackageSupport the package/server combination to get the package file from
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private List<String> symbolicNamesFromDownloadedPackage(final SlingPackageSupport slingPackageSupport) throws URISyntaxException, IOException {
        final File downloadDir = new File(project.getBuildDir() + "/tmp");
        downloadDir.mkdirs();
        final SuccessOrFailure<RuntimePackageProperties> packageInfoSF = RuntimePackageProperties.packageProperties(slingPackageSupport, PackageId.fromString(getPackageName()));
        if (packageInfoSF.failed())
            throw new IllegalStateException("Could not get package information: " + packageInfoSF.getError());
        final RuntimePackageProperties packageInfo = packageInfoSF.getValue();
        LOG.info("Download filename " + packageInfo.getDownloadName());
        final String packageFilename = downloadDir + "/" + packageInfo.getDownloadName();
        final String path = packageInfo.getPath();

        final PackageServerConfiguration packageServerConf = slingPackageSupport.getPackageServerConf();
        final URI zipUri = URI.create(packageServerConf.getPackageDownloadUri() + "?_charset_=utf-8&path=" + path);

        LOG.info("Filename from package list: {}", packageFilename);
        LOG.info("Filepath from package list: {}", path);
        LOG.info("Zip URI from package list: {}", zipUri);

        val file = downloadFile(packageFilename, zipUri, packageServerConf.serverConf);
        val zipFile = new ZipFile(file);
        val zipEntries = Collections.list(zipFile.entries()).stream().
            filter(entry -> entry.getName().endsWith(".jar")).
            collect(Collectors.<ZipEntry>toList());

        final List<String> symbolicNames = new ArrayList<>();
        zipEntries.forEach(it -> {
            String filename = it.getName();
            try {
                val filenameParts = filename.split("/");
                val actualFileName = filenameParts[filenameParts.length - 1];
                val entry = (@NonNull ZipEntry)zipFile.getEntry(filename);
                LOG.debug("Unzipping to " + project.getBuildDir() + "/tmp/" + actualFileName + "...");
                val jarFile = new File(project.getBuildDir() + "/tmp/" + actualFileName);

                try (val is = (@NonNull InputStream)zipFile.getInputStream(entry); val out = new FileOutputStream(jarFile)) {
                    IOUtils.copy(is, out);
                }

                val bundleSymbolicName = getSymbolicName(jarFile);
                if (bundleSymbolicName != null) {
                    symbolicNames.add(bundleSymbolicName);
                }
                else {
                    LOG.warn("{} contains a non-OSGi jar file: {}", file, jarFile);
                }

                LOG.debug("Cleaning up. Deleting {}", jarFile);
                jarFile.delete();
            }
            catch (IOException e) {
                throw new IllegalStateException("Problem handling " + filename + " in " + zipUri, e);
            }
        });
        LOG.info("Bundles from downloaded zipfile: {}", symbolicNames);
        LOG.debug("Cleaning up. Deleting {}", file);
        file.delete();
        return symbolicNames;
    }


    private File downloadFile(String filename, URI uri, SlingServerConfiguration serverConfig) throws IOException {
        HttpGet httpGet = new HttpGet(uri);
        httpGet.addHeader(BasicScheme.authenticate(new UsernamePasswordCredentials(serverConfig.getUsername(), serverConfig.getPassword()), "UTF-8", false));

        HttpClient client = new DefaultHttpClient();
        org.apache.http.HttpResponse httpResponse = client.execute(httpGet);
        File file = new File(filename);

        try (InputStream is = httpResponse.getEntity().getContent(); OutputStream out = new FileOutputStream(file)) {
            IOUtils.copy(is, out);
        }

        return file;
    }


    /**
     * Get the OSGi bundle symbolic name from the file's metadata.
     *
     * @return null if the file is not an OSGi bundle
     */
    public static @Nullable String getSymbolicName(final File file) throws IOException {
        try {
            val jar = new JarFile(file);
            val manifest = jar.getManifest();
            if (manifest == null) return null;
            val entries = manifest.getMainAttributes();
            return entries.getValue("Bundle-SymbolicName");
        }
        catch (ZipException exp) {
            throw new IllegalStateException("Trying to open \'" + file + "\'", exp);
        }
    }


    /**
     * Does the given JAR file have basic OSGi metadata? (Specifically "Bundle-SymbolicName")
     */
    public static boolean isOsgiFile(File file) throws IOException {
        return getSymbolicName(file) != null;
    }


    public final Project getProject() {
        return project;
    }


    public void setPackageManager(PackageManager packageManager) {
        this.packageManager = packageManager;
    }


    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    public interface ServerAction {
        HttpResponse run(SlingSupport slingSupport);
    }

    /**
     * Functional interface for {@link #uninstallAllBundles(List, SlingSupport, UninstallBundlePredicate)}
     */
    public interface UninstallBundlePredicate {
        /**
         * Returns true if the symbolic name passed in should be uninstalled; otherwise false
         */
        boolean eval(String symbolicName);
    }

    public static class DotPrinter {
        private final StringBuilder str = new StringBuilder();


        public String increment() {
            return str.append('.').toString();
        }
    }

}
