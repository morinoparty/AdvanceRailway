/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://papermc.io/repo/repository/maven-public/")
    }
}

// Java 25 のツールチェーンを自動的にプロビジョニングする。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    defaultLibrariesExtensionName = "libs"
}

rootProject.name = "AdvanceRailway"
