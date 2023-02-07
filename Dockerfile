FROM centos:7
COPY ./ ./lineStockBot
RUN yum install -y wget
RUN wget -q https://download.java.net/java/GA/jdk11/13/GPL/openjdk-11.0.1_linux-x64_bin.tar.gz
RUN wget -q https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.6/apache-maven-3.8.6-bin.tar.gz
RUN tar xvf openjdk-11.0.1_linux-x64_bin.tar.gz
RUN tar xvf apache-maven-3.8.6-bin.tar.gz
ENV JAVA_HOME=/jdk-11.0.1
ENV MAVEN_HOME=/apache-maven-3.8.6
ENV PATH=$PATH:/jdk-11.0.1/bin:/apache-maven-3.8.6/bin
ARG CHANNEL_TOKEN
ARG CHANNEL_SECRET
RUN cd /lineStockBot && mvn clean package -DskipTests
CMD java -Dline.bot.channelToken=${CHANNEL_TOKEN} -Dline.bot.channelSecret=${CHANNEL_SECRET} -jar /lineStockBot/target/line-echo-robot-0.1.jar