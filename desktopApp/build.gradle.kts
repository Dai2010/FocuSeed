plugins {
    application
}

dependencies {
    implementation(project(":shared"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    applicationName = "focuseed"
    mainClass.set("io.github.dai2010.focuseed.desktop.FocuSeedDesktop")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
