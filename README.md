# Purpose #

Gradle plugin for working with Adobe CQ/AEM content packages.

[ ![Download](https://api.bintray.com/packages/twcable/aem/gradle-plugin-cq-package/images/download.svg) ](https://bintray.com/twcable/aem/gradle-plugin-cq-package/_latestVersion)  [![Stories in Ready](https://badge.waffle.io/TWCable/gradle-plugin-cq-package.png?label=ready&title=Ready)](https://waffle.io/TWCable/gradle-plugin-cq-package)

# Installation #

```
buildscript {
    repositories {
        jcenter()
        maven {
            url "http://dl.bintray.com/twcable/aem"
        }
    }

    dependencies {
        classpath "com.twcable.gradle:gradle-plugin-cq-package:<version>"
    }
}
```


# CQ Package Plugin #

See [the CQ Package Plugin documentation](docs/CqPackagePlugin.adoc)

# Installation #

```
buildscript {
    repositories {
        jcenter()
        maven {
            url "http://dl.bintray.com/twcable/aem"
        }
    }

    dependencies {
        classpath "com.twcable.gradle:gradle-plugin-cq-package:<version>"
    }
}
```

Tested against **Gradle 3.5** and **Gradle 4.0** using **JDK 1.8**

# API #

https://twcable.github.io/gradle-plugin-cq-package/groovydoc/

# LICENSE

Copyright 2014-2017 Time Warner Cable, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
the specific language governing permissions and limitations under the License.
