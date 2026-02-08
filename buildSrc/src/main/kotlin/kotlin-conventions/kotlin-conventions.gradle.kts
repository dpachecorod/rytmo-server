plugins {
    id("java-library")
    kotlin("jvm")
    kotlin("kapt")
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        ktfmt()
        ktlint().editorConfigOverride(mapOf(
            "max_line_length" to "200"))
        leadingTabsToSpaces()
        trimTrailingWhitespace()
        endWithNewline()
    }
}