pluginManagement {
    repositories {
        // 阿里云镜像 - 优先下载，速度最快
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/jcenter")
        // 官方仓库兜底
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // 新版强制规则，必须保留
    repositories {
        // 阿里云镜像 - 核心配置，所有依赖从这里下载
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/jcenter")
        // 官方仓库
        google()
        mavenCentral()
    }
}

rootProject.name = "LLMuzik"
include(":app")
 