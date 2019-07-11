# maven build
FROM maven:3.6.1-jdk-11-slim AS MAVEN_TOOL_CHAIN
COPY pom-docker.xml /tmp/pom.xml
COPY src /tmp/src/
COPY settings-docker.xml /usr/share/maven/ref/
WORKDIR /tmp/
RUN mvn -s /usr/share/maven/ref/settings-docker.xml package

# jlinked java 11 (do NOT use alpine-slim here which has important module files deleted)
FROM adoptopenjdk/openjdk11:jdk-11.0.3_7-alpine AS JLINKED_JAVA
RUN ["jlink", "--compress=2", \
     "--module-path", "/opt/java/openjdk/jmods", \
     "--add-modules", "java.base,java.compiler,java.desktop,java.instrument,java.management,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql.rowset,jdk.httpserver,jdk.jdi,jdk.unsupported", \
     "--output", "/jlinked"]

# final custom slim java image (for apk command see jdk-11.0.3_7-alpine-slim)
FROM alpine:3.9
RUN apk add --no-cache --virtual .build-deps curl binutils \
    && GLIBC_VER="2.29-r0" \
    && ALPINE_GLIBC_REPO="https://github.com/sgerrand/alpine-pkg-glibc/releases/download" \
    && GCC_LIBS_URL="https://archive.archlinux.org/packages/g/gcc-libs/gcc-libs-8.2.1%2B20180831-1-x86_64.pkg.tar.xz" \
    && GCC_LIBS_SHA256=e4b39fb1f5957c5aab5c2ce0c46e03d30426f3b94b9992b009d417ff2d56af4d \
    && ZLIB_URL="https://archive.archlinux.org/packages/z/zlib/zlib-1%3A1.2.11-2-x86_64.pkg.tar.xz" \
    && ZLIB_SHA256=8d39895fe012f71d76b68c16bf8d333a4765c6db671ebb0e88f4db90335de576 \
    && curl -Ls https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub -o /etc/apk/keys/sgerrand.rsa.pub \
    && SGERRAND_RSA_SHA256="823b54589c93b02497f1ba4dc622eaef9c813e6b0f0ebbb2f771e32adf9f4ef2" \
    && echo "${SGERRAND_RSA_SHA256}  /etc/apk/keys/sgerrand.rsa.pub" | sha256sum -c - \
    && curl -Ls ${ALPINE_GLIBC_REPO}/${GLIBC_VER}/glibc-${GLIBC_VER}.apk > /tmp/${GLIBC_VER}.apk \
    && apk add /tmp/${GLIBC_VER}.apk \
    && curl -Ls ${GCC_LIBS_URL} -o /tmp/gcc-libs.tar.xz \
    && echo "${GCC_LIBS_SHA256}  /tmp/gcc-libs.tar.xz" | sha256sum -c - \
    && mkdir /tmp/gcc \
    && tar -xf /tmp/gcc-libs.tar.xz -C /tmp/gcc \
    && mv /tmp/gcc/usr/lib/libgcc* /tmp/gcc/usr/lib/libstdc++* /usr/glibc-compat/lib \
    && strip /usr/glibc-compat/lib/libgcc_s.so.* /usr/glibc-compat/lib/libstdc++.so* \
    && curl -Ls ${ZLIB_URL} -o /tmp/libz.tar.xz \
    && echo "${ZLIB_SHA256}  /tmp/libz.tar.xz" | sha256sum -c - \
    && mkdir /tmp/libz \
    && tar -xf /tmp/libz.tar.xz -C /tmp/libz \
    && mv /tmp/libz/usr/lib/libz.so* /usr/glibc-compat/lib \
    && apk del --purge .build-deps \
    && rm -rf /tmp/${GLIBC_VER}.apk /tmp/gcc /tmp/gcc-libs.tar.xz /tmp/libz /tmp/libz.tar.xz /var/cache/apk/*

ENV JAVA_VERSION jdk-11.0.3+7
COPY --from=JLINKED_JAVA /jlinked /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
PATH="/opt/java/openjdk/bin:$PATH"

# add spring boot application
VOLUME /tmp
COPY --from=MAVEN_TOOL_CHAIN /tmp/target/camunda-showcase-keycloak*.jar ./app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
