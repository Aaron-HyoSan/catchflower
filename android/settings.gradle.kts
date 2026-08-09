pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // 🔴 **카카오맵 SDK는 mavenCentral에 없다.** 실측으로 확인했다 —
        //    `com.kakao.maps.open:android`를 mavenCentral에서 찾으면 **404**다.
        //    이 저장소를 빼면 지도 화면만 못 만드는 게 아니라 **빌드 자체가 죽는다.**
        maven("https://devrepo.kakao.com/nexus/content/groups/public/")
    }
}

rootProject.name = "CatchFlower"
include(":app")
