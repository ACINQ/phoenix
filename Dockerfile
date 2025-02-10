# main build image
FROM ubuntu:24.04

ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
# get latest version number from https://developer.android.com/studio/index.html, bottom section
ENV ANDROID_CMDLINETOOLS_FILE commandlinetools-linux-8092744_latest.zip
ENV ANDROID_CMDLINETOOLS_URL https://dl.google.com/android/repository/${ANDROID_CMDLINETOOLS_FILE}
ENV ANDROID_API_LEVELS android-33
ENV ANDROID_BUILD_TOOLS_VERSION 33.0.2
ENV ANDROID_HOME /usr/local/android-sdk
ENV PATH ${PATH}:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools
ENV JAVA_OPTS "-Dprofile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

# prepare env
RUN apt-get update -y && \
    apt-get install -y software-properties-common locales && \
    apt-get update -y && \
    locale-gen en_US.UTF-8 && \
    apt-get install -y openjdk-17-jdk wget git unzip dos2unix

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
RUN echo y | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" "platforms;${ANDROID_API_LEVELS}"

# copy phoenix project over to docker image
COPY . /home/ubuntu/phoenix
# make sure we don't read properties from the host environment
RUN rm -f /home/ubuntu/phoenix/local.properties
# make sure we use unix EOL files
RUN find /home/ubuntu/phoenix -type f -print0 | xargs -0 dos2unix --
# make gradle wrapper executable
RUN chmod +x /home/ubuntu/phoenix/gradlew
