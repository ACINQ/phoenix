# base image to build eclair-core
FROM adoptopenjdk/openjdk11:jdk-11.0.12_7-alpine as ECLAIR_CORE_BUILD

# this is necessary to extract the eclair-core version that we need to clone for the build
COPY ./buildSrc/src/main/kotlin/Versions.kt .
RUN cat Versions.kt | grep "const val eclair =" | cut -d '"' -f 2 > eclair-core-version.txt

ARG MAVEN_VERSION=3.6.3
ARG USER_HOME_DIR="/root"
ARG SHA=c35a1803a6e70a126e80b2b3ae33eed961f83ed74d18fcd16909b2d44d7dada3203f1ffe726c17ef8dcca2dcaa9fca676987befeadc9b9f759967a8cb77181c0
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN apk add --no-cache curl tar bash git

# setup maven
RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

# clone eclair at the specified branch
RUN git clone https://github.com/ACINQ/eclair -b v$(cat eclair-core-version.txt)

# build eclair-core
RUN cd eclair && mvn install -pl eclair-core -am -Dmaven.test.skip=true

# main build image
FROM ubuntu:20.10

ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
# get latest version number from https://developer.android.com/studio/index.html, bottom section
ENV ANDROID_CMDLINETOOLS_FILE commandlinetools-linux-7583922_latest.zip
ENV ANDROID_CMDLINETOOLS_URL https://dl.google.com/android/repository/${ANDROID_CMDLINETOOLS_FILE}
ENV ANDROID_API_LEVELS android-30
ENV ANDROID_BUILD_TOOLS_VERSION 30.0.2
ENV ANDROID_NDK_VERSION 21.3.6528147
ENV CMAKE_VERSION 3.18.1
ENV ANDROID_HOME /usr/local/android-sdk
ENV PATH ${PATH}:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools
ENV JAVA_OPTS "-Dprofile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

# prepare env
RUN apt-get update -y && \
    apt-get install -y software-properties-common locales && \
    apt-get update -y && \
    locale-gen en_US.UTF-8 && \
    apt-get install -y openjdk-11-jdk wget git unzip dos2unix

# fetch and unpack the android sdk
RUN mkdir /usr/local/android-sdk && \
    cd /usr/local/android-sdk && \
    wget -q ${ANDROID_CMDLINETOOLS_URL} && \
    unzip ${ANDROID_CMDLINETOOLS_FILE} && \
    mv cmdline-tools latest && \
    mkdir cmdline-tools && \
    mv latest cmdline-tools && \
    rm ${ANDROID_CMDLINETOOLS_FILE}

# install sdk packages
RUN echo y | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" "cmake;${CMAKE_VERSION}" "ndk;${ANDROID_NDK_VERSION}" "patcher;v4" "platforms;${ANDROID_API_LEVELS}"

# build tor library
RUN git clone https://github.com/ACINQ/Tor_Onion_Proxy_Library && \
    cd Tor_Onion_Proxy_Library && \
    ./gradlew install && \
    ./gradlew :universal:build && \
    ./gradlew :android:build && \
    ./gradlew :android:publishToMaven

# copy eclair-core dependency
COPY --from=ECLAIR_CORE_BUILD /root/.m2/repository/fr/acinq/eclair /root/.m2/repository/fr/acinq/eclair
# copy phoenix project over to docker image
COPY . /home/ubuntu/phoenix
# make sure we don't read properties the host environment
RUN rm -f /home/ubuntu/phoenix/local.properties
# make sure we use unix EOL files
RUN find /home/ubuntu/phoenix -type f -print0 | xargs -0 dos2unix --
# make gradle wrapper executable
RUN chmod +x /home/ubuntu/phoenix/gradlew
