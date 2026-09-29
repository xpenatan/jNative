pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "jNative"

include(":jNative:core")
include(":jNative:api")
include(":jNative:runtime")
include(":jNative:compiler")
include(":jNative:backend:cpp")
include(":jNative:toolchain:cmake")
include(":jNative:interop")
include(":jNative:cli")
include(":jNative:classlib")
include(":tests:conformance")
include(":samples:hello-world")
include(":samples:native-interop")
include(":samples:native-callbacks")
include(":samples:reflection")
include(":samples:diagnostics")
include(":samples:portability")
