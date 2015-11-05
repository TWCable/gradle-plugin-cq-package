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

import groovy.transform.CompileStatic

@CompileStatic
class Status {
    final String name


    protected Status(String name) {
        this.name = name
    }


    static final Status OK = new Status("OK")
    static final Status UNKNOWN = new Status("UNKNOWN")
    static final Status SERVER_INACTIVE = new Status("SERVER_INACTIVE")
    static final Status SERVER_TIMEOUT = new Status("SERVER_TIMEOUT")


    @Override
    public String toString() {
        return name;
    }

}
