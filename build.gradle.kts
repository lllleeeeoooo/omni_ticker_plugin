import org.gradle.api.Action
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    // JetBrains-hosted platform tooling (java-compiler-ant-tasks, etc.)
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
    intellijPlatform {
        // Needed to consume the bundled platform artifacts of a local() IDE
        // dependency (WebStorm 2025.2.1 on this machine).
        localPlatformArtifacts()
    }
}

// group 直接写死；version 不再显式赋值——唯一基线来源是 gradle.properties 的 `version`，
// 见文末打包计数逻辑（会按 主.次.打包次数 自动生成实际版本）。
group = "com.omniticker"

dependencies {
    intellijPlatform {
        // Local IDE as the SDK: this machine runs portable WebStorm 2025.2.1
        // (build 252) — the same generation we target (since-build=252).
        local("E:/迁移/WebStorm-2025.2.1.win")
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }
    }
    buildSearchableOptions = false
}

tasks {
    test {
        useJUnit()
    }
}

// ============================================================================
// 自动打包计数版本号：基线版本 + 第四段打包次数（如 0.1.0.11）
//   - 基线（主.次.修订）：唯一来源 gradle.properties 的 `version`（如 0.1.0）
//   - 打包次数：持久化在 .gradle/version-counter.txt，每次真正打包自动 +1
//   - 升级重置：手动修改 gradle.properties 的 version（基线变化）后，计数自动归 0
//     （下个包如 0.2.0.0，再打 0.2.0.1 …）
//   - 只在任务图确定本次会执行 buildPlugin 时才递增；./gradlew tasks、IDE sync 不会误增
// ============================================================================
gradle.taskGraph.whenReady(object : Action<TaskExecutionGraph> {
    override fun execute(graph: TaskExecutionGraph) {
        if (graph.allTasks.none { it.name == "buildPlugin" }) return

        val base = project.version.toString()
        // 计数器文件：一行 "基线|次数"，例如 "0.1.0|11"
        val counterFile = file(".gradle/version-counter.txt")
        val old = if (counterFile.exists()) counterFile.readText().trim().split('|') else emptyList()
        val oldBaseline = old.getOrNull(0) ?: ""
        val oldCount = old.getOrNull(1)?.toIntOrNull() ?: 0
        val count = if (oldBaseline == base) {
            // 同基线：上次计数 +1
            oldCount + 1
        } else {
            // 首次打包，或基线升级：第四段归 0 重新计数
            0
        }
        counterFile.parentFile.mkdirs()
        counterFile.writeText("$base|$count")

        val buildVersion = "$base.$count"
        project.version = buildVersion
        // 显式同步产物名与 plugin.xml 版本，不依赖 provider 的惰性求值时机
        tasks.named<Zip>("buildPlugin") { archiveFileName.set("${project.name}-$buildVersion.zip") }
        tasks.named<Jar>("jar") { archiveFileName.set("${project.name}-$buildVersion.jar") }
        (tasks.findByName("patchPluginXml") as PatchPluginXmlTask?)?.pluginVersion?.set(buildVersion)
    }
})