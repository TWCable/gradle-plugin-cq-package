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

import com.twcable.gradle.http.HttpResponse
import groovy.transform.TypeChecked

import static java.net.HttpURLConnection.HTTP_OK

@TypeChecked
@SuppressWarnings(["GroovyUnusedDeclaration"])
class PackageServerFixture {

    static HttpResponse successfulPackageUpload() {
        return new HttpResponse(HTTP_OK, '{"success": true, "msg": "File uploaded"}')
    }


    static HttpResponse successfulInstallPackage() {
        return new HttpResponse(HTTP_OK, '{"success": true, "msg": "File installed"}')
    }


    static HttpResponse successfulUninstallPackage() {
        return new HttpResponse(HTTP_OK, '{"success": true, "msg": "File uninstalled"}')
    }


    static HttpResponse noSnapshotUninstallPackage() {
        return new HttpResponse(HTTP_OK, '{"success":false,"msg":"org.apache.jackrabbit.vault.packaging.PackageException: Unable to uninstall package. No snapshot present."}')
    }


    static HttpResponse successfulDeletePackage() {
        return new HttpResponse(HTTP_OK, '{"success": true, "msg": "File deleted"}')
    }


    static HttpResponse uploadedPackageAlreadyExists(String packageName) {
        return new HttpResponse(HTTP_OK, "{\"success\":false, \"msg\":\"Package already exists: ${packageName}\"}")
    }


    static Map<String, Object> packageList(String packageName) {
        return packageList(
            PackageFixture.of("day/cq550/product:cq-content:5.5.0.20120220").
                dependency("froblez", null).
                dependency("day/cq550/product:groblez", "day/cq550/product:groblez:1.2.3"),
            PackageFixture.of("twc/test:${packageName}:1.0.1-SNAPSHOT")
        )
    }


    static Map<String, Object> packageList(PackageFixture... packageFixtures) {
        def resultsMap = packageFixtures.collect { PackageFixture fixture -> toMap(fixture) }

        return [
            results: resultsMap,
            total  : resultsMap.size()
        ]
    }


    static Map<String, Object> toMap(PackageFixture fixture) {
        Map<String, Object> map = [
            pid            : fixture.packageId().toString(),
            path           : fixture.packageId().getInstallationPath(),
            name           : fixture.packageId().getName(),
            downloadName   : fixture.packageId().getDownloadName(),
            group          : fixture.packageId().getGroup(),
            groupTitle     : fixture.groupTitle(),
            version        : fixture.packageId().getVersionString(),
            buildCount     : fixture.buildCount(),
            created        : fixture.created(),
            createdBy      : fixture.createdBy(),
            lastUnpacked   : fixture.lastUnpacked(),
            lastUnpackedBy : fixture.lastUnpackagedBy(),
            lastUnwrapped  : fixture.lastUnwrapped(),
            lastUnwrappedBy: fixture.lastUnwrappedBy(),
            size           : fixture.size(),
            hasSnapshot    : fixture.hasSnapshot(),
            needsRewrap    : fixture.needsRewrap(),
            builtWith      : fixture.builtWith(),
            requiresRoot   : fixture.requiresRoot(),
            requiresRestart: fixture.requiresRestart(),
            acHandling     : fixture.acHandling(),
            dependencies   : fixture.dependencies().collect { dp ->
                [name: dp.dependency.toString(), id: dp.packageId != null ? dp.packageId.toString() : '']
            },
        ]

        if (fixture.description() != null) map.description = fixture.description()
        if (fixture.thumbnail() != null) map.thumbnail = fixture.thumbnail()

        return map
    }

}

//        return [
//            results: [
//                [
//                    pid            : "day/cq550/product:cq-content:5.5.0.20120220",
//                    path           : "/etc/packages/day/cq550/product/cq-content-5.5.0.20120220.zip",
//                    name           : "cq-content",
//                    downloadName   : "cq-content-5.5.0.20120220.zip",
//                    group          : "day/cq550/product",
//                    groupTitle     : "day",
//                    version        : "5.5.0.20120220",
//                    description    : "Default installation package that contains repository content for CQ5.",
//                    thumbnail      : "/crx/packmgr/thumbnail.jsp?_charset_=utf-8&path=%2fetc%2fpackages%2fday%2fcq550%2fproduct%2fcq-content-5.5.0.20120220.zip&ck=1345049949526",
//                    buildCount     : 0,
//                    created        : 1329751518460,
//                    createdBy      : "Adobe Systems Incorporated",
//                    lastUnpacked   : 1345049995338,
//                    lastUnpackedBy : "admin",
//                    lastUnwrapped  : 1345049949532,
//                    lastUnwrappedBy: "admin",
//                    size           : 78532000,
//                    hasSnapshot    : false,
//                    needsRewrap    : false,
//                    builtWith      : "Adobe CQ5-5.5.0.SNAPSHOT",
//                    requiresRoot   : false,
//                    requiresRestart: false,
//                    acHandling     : "merge_preserve",
//                    filter         : [
//                        [root: "/var", rules: [[modifier: "include", pattern: "/var/classes(/.*)?"],
//                                               [modifier: "include", pattern: "/var/audit"],
//                                               [modifier: "include", pattern: "/var/proxy(/.*)?"]]],
//                        [root: "/", rules: [[modifier: "include", pattern: "/"],
//                                            [modifier: "include", pattern: "/var"]]]],
//                    screenshots    : [
//                        "/etc/packages/day/cq550/product/cq-content-5.5.0.20120220.zip/jcr:content/vlt:definition/screenshots/90d23940-7b63-4631-bea7-014326b418d3",
//                        "/etc/packages/day/cq550/product/cq-content-5.5.0.20120220.zip/jcr:content/vlt:definition/screenshots/7fbe6b69-e0bc-49b0-8cdb-85b257986aee"
//                    ],
//                    dependencies   : [
//                        [name: "froblez", id: ""],
//                        [name: "day/cq550/product:groblez", id: "day/cq550/product:groblez:1.2.3"],
//                    ],
//                ],
//                [
//                    pid            : "twc/webcms:fakepackage:1.0.1-SNAPSHOT",
//                    path           : "/etc/packages/fakepackage-1.0.1-SNAPSHOT.zip",
//                    name           : packageName,
//                    downloadName   : "fakepackage-1.0.1-SNAPSHOT.zip",
//                    group          : "twc/webcms",
//                    groupTitle     : "twc",
//                    version        : "1.0.1-SNAPSHOT",
//                    description    : "FakePackage - JCR",
//                    thumbnail      : "/crx/packmgr/thumbnail.jsp?_charset_=utf-8&path=%2fetc%2fpackages%2ffakepackage-1.0.1-SNAPSHOT.zip&ck=1359577761522",
//                    buildCount     : 1,
//                    lastUnwrapped  : 1359577685665,
//                    size           : 24277786,
//                    hasSnapshot    : false,
//                    needsRewrap    : true,
//                    builtWith      : "Adobe CQ5-5.5.0",
//                    requiresRoot   : true,
//                    requiresRestart: false,
//                    acHandling     : "overwrite",
//                    dependencies   : [],
//                    providerName   : "Time Warner Cable",
//                    providerUrl    : "http://www.timewarnercable.com",
//                    filter         : [
//                        [root: "/apps/common", rules: []],
//                        [root: "/etc/clientlibs/common", rules: []],
//                        [root: "/etc/designs/common", rules: [[modifier: "exclude", pattern: "/etc/designs/common/jcr:content/(.*)?"]]],
//                        [root: "/var/classes/org/apache/jsp/apps", rules: []],
//                        [root: "/var/clientlibs/apps", rules: []],
//                        [root: "/var/clientlibs/etc", rules: []]],
//                    screenshots    : []
//                ]
//            ],
//            total  : 16]

//            filter         : [
//                [root: "/var", rules: [[modifier: "include", pattern: "/var/classes(/.*)?"],
//                                       [modifier: "include", pattern: "/var/audit"],
//                                       [modifier: "include", pattern: "/var/proxy(/.*)?"]]],
//                [root: "/", rules: [[modifier: "include", pattern: "/"],
//                                    [modifier: "include", pattern: "/var"]]]],
//            screenshots    : [
//                "/etc/packages/day/cq550/product/cq-content-5.5.0.20120220.zip/jcr:content/vlt:definition/screenshots/90d23940-7b63-4631-bea7-014326b418d3",
//                "/etc/packages/day/cq550/product/cq-content-5.5.0.20120220.zip/jcr:content/vlt:definition/screenshots/7fbe6b69-e0bc-49b0-8cdb-85b257986aee"
//            ],
