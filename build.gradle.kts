import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default
import net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
}

group = "me.chaddtheman"
version = "2.0.0"
description = "Interactive chest-inventory menus, configured entirely in game."

// Paper publishes stable builds as <mc>.build.<n>-stable. 26.3 exists but is alpha-only as
// of 2026-09-18; see DECISIONS.md #58.
val minecraftVersion = "26.2"
val paperApiVersion = "26.2.build.124-stable"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // Not shaded: MyMenuLoader downloads these at server start from the list plugin-yml
    // writes to paper-libraries.json, so the versions here are the only copy.
    paperLibrary("com.zaxxer:HikariCP:7.1.0")
    paperLibrary("com.mysql:mysql-connector-j:26.7.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
    }

    processResources {
        // /mymenu changelog reads this from the jar.
        from(rootDir) { include("CHANGELOG.md") }
    }

    runServer {
        minecraftVersion(minecraftVersion)
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}

paper {
    name = "MyMenu"
    main = "me.chaddtheman.mymenu.MyMenu"
    loader = "me.chaddtheman.mymenu.MyMenuLoader"
    generateLibrariesJson = true
    apiVersion = minecraftVersion
    authors = listOf("ChaddTheMan")
    website = "https://github.com/ChaddTheMan/MyMenu"

    serverDependencies {
        // join-classpath is what lets our isolated classloader see PlaceholderAPI's classes.
        register("PlaceholderAPI") {
            load = RelativeLoadOrder.BEFORE
            required = false
            joinClasspath = true
        }
    }

    permissions {
        register("MyMenu.*") {
            default = Default.OP
            children = listOf("MyMenu.admin.*")
        }
        register("MyMenu.admin.*") {
            default = Default.OP
            children = listOf(
                "MyMenu.help.admin",
                "MyMenu.admin.reload",
                "MyMenu.admin.save",
                "MyMenu.admin.update",
                "MyMenu.admin.joinmenu",
                "MyMenu.admin.menu.*",
                "MyMenu.admin.item.*",
            )
        }
        register("MyMenu.help.admin") {
            default = Default.OP
            children = listOf("MyMenu.help")
        }
        register("MyMenu.help") {
            default = Default.OP
            children = listOf("MyMenu.help.player")
        }
        register("MyMenu.help.player") { default = Default.TRUE }
        listOf("reload", "save", "update", "joinmenu").forEach {
            register("MyMenu.admin.$it") { default = Default.OP }
        }
        val menuNodes = listOf(
            "list", "open", "open.other", "edit", "create", "delete", "set", "unset", "give", "info",
        )
        register("MyMenu.admin.menu.*") {
            default = Default.OP
            children = menuNodes.map { "MyMenu.admin.menu.$it" }
        }
        menuNodes.forEach { register("MyMenu.admin.menu.$it") { default = Default.OP } }
        register("MyMenu.admin.item.*") {
            default = Default.OP
            children = listOf("MyMenu.admin.item.name")
        }
        register("MyMenu.admin.item.name") { default = Default.OP }

        // Deliberately outside MyMenu.* so ops still experience cooldowns while testing them.
        register("MyMenu.bypass.cooldown") { default = Default.FALSE }
    }
}
