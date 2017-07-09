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

import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

import static com.twcable.gradle.GradleUtils.extension

/**
 * <h1>Plugin name</h1>
 * "com.twcable.cq-package-base"
 *
 * <h1>Description</h1>
 * Adds capabilities for working with CQ/Jackrabbit/Sling Packages.
 * <p>
 * See <a href="https://github.com/TWCable/gradle-plugin-cq-package/blob/master/docs/CqPackagePlugin.adoc">the plugin
 * documentation</a> for specifics on how this is expected to work.
 */
@Slf4j
@CompileStatic
@SuppressWarnings(["GroovyResultOfAssignmentUsed", "GrMethodMayBeStatic"])
class CqPackageBasePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        log.info("Applying ${this.class.name} to ${project}")

        project.plugins.apply(BasePlugin)
    }

}
