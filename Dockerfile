FROM ubuntu:18.10

ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV ANDROID_SDK_FILENAME sdk-tools-linux-4333796.zip
ENV ANDROID_SDK_URL https://dl.google.com/android/repository/${ANDROID_SDK_FILENAME}
ENV ANDROID_API_LEVELS android-29
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV ANDROID_HOME /usr/local/android-sdk
ENV PATH ${PATH}:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools
ENV JAVA_OPTS "-Dprofile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

# prepare env
RUN apt-get update -y && \
    apt-get install -y software-properties-common locales && \
    apt-get update -y && \
    locale-gen en_US.UTF-8 && \
    apt-get install -y openjdk-8-jdk wget git unzip

# fetch and unpack the android sdk
RUN mkdir /usr/local/android-sdk && \
    cd /usr/local/android-sdk && \
    wget -q ${ANDROID_SDK_URL} && \
    unzip ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME}

# install sdk packages
RUN echo y | $ANDROID_HOME/tools/bin/sdkmanager "build-tools;28.0.3" "cmake;3.6.4111459" "ndk;21.0.6113669" "patcher;v4" "platforms;android-29"

# copy project over to docker image
COPY . /home/ubuntu/phoenix

# copy local maven repo
RUN  mkdir -p /root/.m2/repository && \
     cp -R /home/ubuntu/phoenix/libs/fr /root/.m2/repository

# make gradle wrapper executable
RUN chmod +x /home/ubuntu/phoenix/gradlew
