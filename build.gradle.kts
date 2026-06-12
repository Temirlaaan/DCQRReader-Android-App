// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kgp.android) apply false
    alias(libs.plugins.kgp.serialization) apply false
    alias(libs.plugins.compose.plugin) apply false
    alias(libs.plugins.hilt.plugin) apply false
    alias(libs.plugins.ksp) apply false
}
