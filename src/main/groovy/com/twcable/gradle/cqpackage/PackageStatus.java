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

public class PackageStatus extends Status {
    protected PackageStatus(String name) {
        super(name);
    }


    public static final PackageStatus NOT_INSTALLED = new PackageStatus("NOT_INSTALLED");
    public static final PackageStatus NO_PACKAGE = new PackageStatus("NO_PACKAGE");
    public static final PackageStatus PACKAGE_EXISTS = new PackageStatus("PACKAGE_EXISTS");
    public static final PackageStatus UNRESOLVED_DEPENDENCIES = new PackageStatus("UNRESOLVED_DEPENDENCIES");
}
