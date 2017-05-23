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

import com.twcable.gradle.sling.SlingSupport
import groovy.transform.TypeChecked

/**
 * Brings together a {@link PackageServerConfiguration} and {@link SlingSupport}
 */
@TypeChecked
@SuppressWarnings("GrFinalVariableAccess")
class SlingPackageSupport {
    final PackageServerConfiguration packageServerConf
    final SlingSupport slingSupport


    SlingPackageSupport(PackageServerConfiguration packageServerConf, SlingSupport slingSupport) {
        if (packageServerConf == null) throw new IllegalArgumentException("packageServerConf == null")
        if (slingSupport == null) throw new IllegalArgumentException("slingSupport == null")
        this.packageServerConf = packageServerConf
        this.slingSupport = slingSupport
    }


    boolean isActive() {
        return packageServerConf.serverConf.active
    }


    void setActive(boolean isActive) {
        packageServerConf.serverConf.active = isActive
    }


    @Override
    public String toString() {
        return 'SlingPackageSupport{' + packageServerConf + '}'
    }
}
