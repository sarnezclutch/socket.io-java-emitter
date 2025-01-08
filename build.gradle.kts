plugins {
    id("java")
    id("application") // Apply the application plugin
    id("maven-publish") // Apply the Maven Publish plugin
}

group = "quadra"
version = "1.0.0"

repositories {
    mavenCentral()

    // Add Azure DevOps Maven repository
    maven {
        url = uri("https://pkgs.dev.azure.com/novaduverauk/7896a4d2-d99f-4444-9477-f3835abcba44/_packaging/quadra-gradle/maven/v1")
        name = "quadra-gradle"
        credentials {
            username = "novaduverauk"
            password = System.getenv("AZURE_DEVOPS_PAT") // Use environment variable for Personal Access Token
        }
    }
}

dependencies {
    // Test dependencies
    testImplementation("junit:junit:3.8.1")

    // Runtime dependencies
    implementation("commons-codec:commons-codec:1.9")
    implementation("org.apache.commons:commons-pool2:2.2")
    implementation("com.google.code.gson:gson:2.2.4")
    implementation("javassist:javassist:3.12.1.GA")
    implementation("org.msgpack:msgpack:0.6.11")
    implementation("org.json:json:20140107")
    implementation("org.redisson:redisson:3.40.0")
}

tasks.test {
    useJUnitPlatform()
}


// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "quadra" // Set your group ID
            artifactId = "quadra-socket.io-redis-emitter-java" // Replace with your desired artifact ID
            version = "1.0.0" // Set your version
        }
    }

    repositories {
        maven {
            url = uri("https://pkgs.dev.azure.com/novaduverauk/7896a4d2-d99f-4444-9477-f3835abcba44/_packaging/quadra-gradle/maven/v1")
            name = "quadra-gradle"
            credentials {
                username = "novaduverauk"
                password = System.getenv("AZURE_DEVOPS_PAT") // Use environment variable for Personal Access Token
            }
        }
    }
}