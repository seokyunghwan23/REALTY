plugins {
	java
	id("org.springframework.boot") version "3.4.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.realty"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.osgeo.org/repository/release/") }
	maven { url = uri("https://download.osgeo.org/webdav/geotools/") }
	maven { url = uri("https://repo.osgeo.org/repository/snapshot/") }
	maven { url = uri("https://maven.geo-solutions.it/") }
}

dependencies {
    // HTML to PDF (openhtmltopdf)
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")

    // Thymeleaf (HTML 템플릿)
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    implementation("com.google.apis:google-api-services-sheets:v4-rev20240416-2.0.0")

    // Google API 클라이언트 코어
    implementation("com.google.api-client:google-api-client:2.2.0")

    // 서비스 계정 인증 및 OAuth2 처리를 위한 라이브러리 (Jetty는 개발 환경용으로 포함)
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")

    // JSON 직렬화/역직렬화를 위한 GSON 라이브러리 (Google API Client에서 사용)
    implementation("com.google.api-client:google-api-client-gson:2.2.0")
	// WebFlux 사용 (WebClient 기반)
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	// JPA & Hibernate
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.hibernate:hibernate-core:6.2.0.Final")
	implementation("org.hibernate:hibernate-spatial:6.2.0.Final")
	implementation("org.locationtech.jts:jts-core:1.18.2")
	implementation("org.jboss.logging:jboss-logging:3.5.3.Final")

	// Lombok
	compileOnly("org.projectlombok:lombok:1.18.30")
	annotationProcessor("org.projectlombok:lombok:1.18.30")

	// JSON 파싱
	implementation("com.googlecode.json-simple:json-simple:1.1.1")

	// HTML 파싱
	implementation("org.jsoup:jsoup:1.17.2")

	// MySQL 드라이버
	runtimeOnly("com.mysql:mysql-connector-j")
	implementation("com.mysql:mysql-connector-j:8.0.33")
	// Apache HttpClient 5 (TLS 오류 해결)
	implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

	// 상권 분석 - 좌표 변환 (경량 라이브러리)
	implementation("org.locationtech.proj4j:proj4j:1.1.5")

	// 상권 분석 - OpenAI
	implementation("com.theokanning.openai-gpt3-java:service:0.18.2")

	// 상권 분석 - Google Gemini (공식 SDK)
	implementation("com.google.genai:google-genai:1.28.0")

	// DevTools & Test
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")


}




tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
	options.compilerArgs.add("-parameters")
}
