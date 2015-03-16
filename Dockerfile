# Based on ahazem/android-dockerfile for 12.04 LTS, version 0.0.1

FROM phusion/baseimage:0.9.16

MAINTAINER Jack Twilley <mathuin@gmail.com>

# App versions
ENV ANDROID_TARGET 8
ENV ANT_VER 1.9.4
ENV BT_VER 22.0.0
ENV JDK_VER 8
ENV NDK_VER 10d
ENV SDK_VER 24.0.2

# Files
ENV ANT_FILE apache-ant-${ANT_VER}-bin.tar.gz
ENV NDK_FILE android-ndk-r${NDK_VER}-linux-x86_64.bin
ENV SDK_FILE android-sdk_r${SDK_VER}-linux.tgz

# Use squid-deb-proxy if present.
ENV DIRECT_HOSTS ppa.launchpad.net download.oracle.com

RUN route -n | awk '/^0.0.0.0/ {print $2}' > /tmp/host_ip.txt
RUN echo "HEAD /" | nc `cat /tmp/host_ip.txt` 8000 | grep squid-deb-proxy \
  && (echo "Acquire::http::Proxy \"http://$(cat /tmp/host_ip.txt):8000\";" > /etc/apt/apt.conf.d/30proxy) \
  && (for host in ${DIRECT_HOSTS}; do echo "Acquire::http::Proxy::$host DIRECT;" >> /etc/apt/apt.conf.d/30proxy; done) \
  || echo "No squid-deb-proxy detected on docker host"

# Never ask for confirmations
ENV DEBIAN_FRONTEND noninteractive
RUN echo "debconf shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections
RUN echo "debconf shared/accepted-oracle-license-v1-1 seen true" | debconf-set-selections

# Update apt and install packages
RUN apt-get update && apt-get install -y \
    lib32bz2-1.0 \
    lib32ncurses5 \
    lib32stdc++6 \
    lib32z1 \
    python-software-properties \
    software-properties-common

# Add oracle-jdk to repositories
RUN add-apt-repository ppa:webupd8team/java

# Make sure the package repository is up to date
# RUN echo "deb http://archive.ubuntu.com/ubuntu trusty main universe" >> /etc/apt/sources.list

# Update apt
RUN apt-get update && apt-get install -y \
    oracle-java${JDK_VER}-installer

# Put everything in /usr/local
WORKDIR /usr/local

# Install android sdk
RUN wget http://dl.google.com/android/${SDK_FILE}
RUN tar -xvzf ${SDK_FILE}
RUN rm ${SDK_FILE}
RUN mv android-sdk-linux android-sdk

# Install android ndk
# JMT: disabled as I don't use it
RUN wget http://dl.google.com/android/ndk/${NDK_FILE}
RUN chmod +x ${NDK_FILE}
RUN ./${NDK_FILE}
RUN rm ${NDK_FILE}
RUN mv android-ndk-r${NDK_VER} android-ndk

# Install apache ant
RUN wget http://archive.apache.org/dist/ant/binaries/${ANT_FILE}
RUN tar -xvzf ${ANT_FILE}
RUN mv apache-ant-${ANT_VER} apache-ant
RUN rm ${ANT_FILE}

# Add android tools and platform tools to PATH
ENV ANDROID_HOME /usr/local/android-sdk
ENV PATH $PATH:$ANDROID_HOME/tools
ENV PATH $PATH:$ANDROID_HOME/platform-tools
ENV PATH $PATH:$ANDROID_HOME/build-tools/${BT_VER}

# Add ant to PATH
ENV ANT_HOME /usr/local/apache-ant
ENV PATH $PATH:$ANT_HOME/bin

# Export JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-${JDK_VER}-oracle

# Install proper version of Android target.
RUN echo "y" | android update sdk --no-ui --filter platform-tools,android-${ANDROID_TARGET},build-tools-${BT_VER}

# Application source directory
RUN mkdir -p /app/src /app/test /app/bin /app/gen
WORKDIR /app
COPY build-hfbeacon.py /app/build-hfbeacon.py
RUN chmod +x /app/build-hfbeacon.py
CMD [ "python", "/app/build-hfbeacon.py", "help" ]
