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
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingBundleFixture
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServerFixture
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import com.twcable.gradle.sling.osgi.BundleServerConfiguration
import com.twcable.gradle.sling.osgi.BundleState
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import com.twcable.gradle.sling.osgi.SlingBundleSupport
import com.twcable.gradle.sling.osgi.SlingProjectBundleConfiguration
import groovy.json.JsonBuilder
import groovy.transform.TypeChecked
import nebula.test.ProjectSpec
import org.apache.jackrabbit.vault.packaging.PackageId
import org.apache.jackrabbit.vault.packaging.PackageManager
import org.apache.jackrabbit.vault.packaging.VaultPackage
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPlugin
import spock.lang.Subject

import static com.twcable.gradle.sling.osgi.BundleState.ACTIVE
import static com.twcable.gradle.sling.osgi.BundleState.FRAGMENT
import static com.twcable.gradle.sling.osgi.BundleState.INSTALLED
import static com.twcable.gradle.sling.osgi.BundleState.RESOLVED
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings(["GroovyAssignabilityCheck", "GroovyAccessibility", "GroovyPointlessBoolean"])
class CqPackageHelperSpec extends ProjectSpec {
    @Subject
    CqPackageHelper cqPackageHelper

    SlingServersConfiguration slingServersConfiguration
    SlingServerConfiguration slingServerConfiguration
    BundleServerConfiguration _serverConf
    SlingSupport _slingSupport


    def setup() {
        slingServersConfiguration = project.extensions.create(SlingServersConfiguration.NAME, SlingServersConfiguration, project)
        slingServerConfiguration = new SlingServerConfiguration().with {
            maxWaitMs = 1_000
            retryWaitMs = 10
            active = true
            machineName = 'test'
            it
        }
        slingServersConfiguration.servers.clear()
        slingServersConfiguration.servers.put(slingServerConfiguration.name, slingServerConfiguration)

        project.plugins.apply(JavaPlugin)

        cqPackageHelper = project.extensions.create(CqPackageHelper.NAME, CqPackageHelper, project)

        project.tasks.create('createPackage', CreatePackageTask)
    }


    def "upload package, no problems"() {
        System.setProperty('package', projectDir.absolutePath)

        def json = new JsonBuilder(PackageServerFixture.packageList("fakepackage"))
        1 * slingSupport.doGet(_) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _) >> PackageServerFixture.successfulPackageUpload()

        cqPackageHelper.packageManager = Mock(PackageManager) {
            open(_) >> {
                return Mock(VaultPackage) {
                    getId() >> new PackageId("", "fakepackage", "1.2.3")
                }
            }
        }

        expect:
        cqPackageHelper.uploadPackage(slingPackageSupportFactory) == Status.OK

        cleanup:
        System.clearProperty('package')
    }


    def "install package, no problems"() {
        def json = new JsonBuilder(PackageServerFixture.packageList(project.name))
        1 * slingSupport.doGet(_) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _) >> PackageServerFixture.successfulInstallPackage()

        expect:
        cqPackageHelper.installPackage(slingPackageSupportFactory)
    }


    def "uninstall package, no problems"() {
        def json = new JsonBuilder(PackageServerFixture.packageList(project.name))
        1 * slingSupport.doGet(_) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _) >> PackageServerFixture.successfulUninstallPackage()

        expect:
        cqPackageHelper.uninstallPackage(slingPackageSupportFactory)
    }


    def "uninstall package, no snapshot"() {
        def json = new JsonBuilder(PackageServerFixture.packageList(project.name))
        1 * slingSupport.doGet(_) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _) >> PackageServerFixture.noSnapshotUninstallPackage()

        expect:
        cqPackageHelper.uninstallPackage(slingPackageSupportFactory)
    }


    def "delete package, no problems"() {
        def json = new JsonBuilder(PackageServerFixture.packageList(project.name))
        1 * slingSupport.doGet(_) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _) >> PackageServerFixture.successfulDeletePackage()

        expect:
        cqPackageHelper.deletePackage(slingPackageSupportFactory)
    }


    def "upload package, package exists"() {
        System.setProperty('package', projectDir.absolutePath)

        cqPackageHelper.packageManager = Mock(PackageManager) {
            open(_) >> {
                return Mock(VaultPackage) {
                    getId() >> new PackageId("", "fakepackage", "1.2.3")
                }
            }
        }

        1 * slingSupport.doPost(_, _) >>
            PackageServerFixture.uploadedPackageAlreadyExists("fakepackage")

        when:
        cqPackageHelper.uploadPackage(slingPackageSupportFactory)

        then:
        def exp = thrown(GradleException)
        exp.message.contains("Package already exists")

        cleanup:
        System.clearProperty('package')
    }


    def "listPackages for a server configuration"() {
        def json = new JsonBuilder(PackageServerFixture.packageList("fakepackage"))
        1 * slingSupport.doGet(_) >> { new HttpResponse(HTTP_OK, json.toString()) }

        expect:
        cqPackageHelper.listPackages(slingPackageSupportFactory.create(slingServerConfiguration))
    }


    def "packageDependencies"() {
        def fixture = PackageFixture.of("day/cq550/product:cq-content:5.5.0.20120220").
            dependency("froblez", null).
            dependency("day/cq550/product:groblez", "day/cq550/product:groblez:1.2.3")

        when:
        def props = RuntimePackageProperties.fromJson(PackageServerFixture.toMap(fixture))

        then:
        props.id.name == "cq-content"
        props.dependencies.collect { it.toString() } as Set == ["froblez", "day/cq550/product:groblez"] as Set
    }


    def "getPackageName"() {
        def packageFile = new File(CqPackageHelperSpec.class.classLoader.getResource("testpackage-1.0.1.zip").getFile())

        when:
        def packageName = CqPackageHelper.packageName(packageFile)

        then:
        packageName == 'testpackage'
    }


    def "validate all bundles: resolved -> active"() {
        given:
        symbolicName = 'b.c.d.e'

        4 * slingSupport.doGet(bundleServerConf.bundlesControlUri) >>> [
            bundlesResp(bundleConfiguration, RESOLVED),
            bundlesResp(bundleConfiguration, RESOLVED),
            bundlesResp(bundleConfiguration, RESOLVED),
            bundlesResp(bundleConfiguration, ACTIVE)
        ]

        when:
        def resp = cqPackageHelper.validateAllBundles([symbolicName], slingSupport)

        then:
        resp.code == HTTP_OK
    }


    def "validate all bundles for null symbolic names: resolved -> active"() {
        given:
        4 * slingSupport.doGet(bundleServerConf.bundlesControlUri) >>> [
            bundlesResp(bundleConfiguration, RESOLVED),
            bundlesResp(bundleConfiguration, RESOLVED),
            bundlesResp(bundleConfiguration, RESOLVED),
            bundlesResp(bundleConfiguration, ACTIVE)
        ]

        when:
        def resp = cqPackageHelper.validateAllBundles(null, slingSupport)

        then:
        resp.code == HTTP_OK
    }


    def "start inactive bundles"() {
        given:
        stubGet getBundleServerConf().bundlesControlUri, bundlesResp(bundleConfiguration, INSTALLED)

        when:
        cqPackageHelper.startInactiveBundles(slingSupport)

        then:
        true // the fact that no exception was thrown shows that it's good
    }


    def "validate all bundles: resolved"() {
        given:
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(bundleConfiguration, RESOLVED)

        when:
        def resp = cqPackageHelper.validateAllBundles([symbolicName], slingSupport)

        then:
        resp.code == HTTP_INTERNAL_ERROR
        resp.body ==~ /Not all bundles for .* are ACTIVE.*/
    }


    def "validate all bundles for null symbolic names: resolved"() {
        given:
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(bundleConfiguration, RESOLVED)

        when:
        def resp = cqPackageHelper.validateAllBundles(null, slingSupport)

        then:
        resp.code == HTTP_INTERNAL_ERROR
        resp.body ==~ /Not all bundles are ACTIVE.*/
    }


    def "validate all bundles: installed"() {
        given:
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(bundleConfiguration, INSTALLED)

        when:
        def resp = cqPackageHelper.validateAllBundles([symbolicName], slingSupport)

        then:
        resp.code == HTTP_INTERNAL_ERROR
        resp.body ==~ /Not all bundles for .* are ACTIVE.*/
    }


    def "validate all bundles: fragment"() {
        given:
        symbolicName = 'b.c.d.e'
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(bundleConfiguration, FRAGMENT)

        when:
        def resp = cqPackageHelper.validateAllBundles([symbolicName], slingSupport)

        then:
        resp.code == HTTP_OK
    }


    def "validate all bundles: missing"() {
        given:
        def serverFixture = new SlingServerFixture(bundles: [new SlingBundleFixture(bundleConfiguration: bundleConfiguration)])

        stubGet bundleServerConf.bundlesControlUri, okResp(serverFixture.bundlesInformationJson(false))

        when:
        def resp = cqPackageHelper.validateAllBundles(['b.c.d.e'], slingSupport)

        then:
        resp.code == HTTP_INTERNAL_ERROR
        resp.body ==~ /Not all bundles .* are ACTIVE.*/
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************

    @TypeChecked
    SlingBundleConfiguration getBundleConfiguration() {
        def bundleConfiguration = project.extensions.findByType(SlingBundleConfiguration)
        if (bundleConfiguration == null) {
            return project.extensions.create(SlingProjectBundleConfiguration.NAME, SlingProjectBundleConfiguration, project)
        }
        return bundleConfiguration
    }


    @TypeChecked
    BundleServerConfiguration getBundleServerConf() {
        if (_serverConf == null) {
            _serverConf = new BundleServerConfiguration(slingServerConfiguration)
        }
        return _serverConf
    }


    @TypeChecked
    void setSymbolicName(String symbolicName) {
        bundleConfiguration.symbolicName = symbolicName
    }


    @TypeChecked
    String getSymbolicName() {
        return bundleConfiguration.symbolicName
    }


    SlingSupport mockSlingSupport() {
        return Mock(SlingSupport) {
            getServerConf() >> bundleServerConf.serverConf
            SimpleHttpClient httpClient = Mock(SimpleHttpClient)
            SlingSupport slingSupport = it
            slingSupport.doHttp(_) >> { Closure closure -> closure.delegate = slingSupport; closure.call(httpClient) }
            0 * /do.*/(*_) // complain if any unexpected calls are made
        }
    }


    @TypeChecked
    SlingBundleSupport getSlingBundleSupport() {
        return new SlingBundleSupport(bundleConfiguration, bundleServerConf, slingSupport)
    }


    @TypeChecked
    SlingSupport getSlingSupport() {
        if (_slingSupport == null) {
            _slingSupport = mockSlingSupport()
        }
        return _slingSupport
    }


    void stubGet(URI uri, HttpResponse response) {
        slingSupport.doGet(uri) >> response
    }


    @TypeChecked
    HttpResponse bundlesResp(SlingBundleConfiguration bundleConfiguration, BundleState state) {
        return new HttpResponse(HTTP_OK, bundlesJson(bundleConfiguration, state))
    }


    @TypeChecked
    String bundlesJson(SlingBundleConfiguration bundleConfiguration, BundleState state) {
        def bundleFixture = new SlingBundleFixture(bundleConfiguration: bundleConfiguration, bundleState: state)

        def serverFixture = new SlingServerFixture(bundles: [bundleFixture])

        return serverFixture.bundlesInformationJson(false)
    }


    @TypeChecked
    public HttpResponse okResp(String body) {
        return new HttpResponse(HTTP_OK, body)
    }


    SimpleSlingPackageSupportFactory getSlingPackageSupportFactory() {
        return new SimpleSlingPackageSupportFactory({ slingSupport })
    }

}
