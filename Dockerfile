FROM openkbs/jdk11-mvn-py3
COPY ./ ./
CMD mvn clean package -DskipTests
ARG CHANNEL_TOKEN
ARG CHANNEL_SECRET
ENTRYPOINT ["java","-jar","-Dline.bot.channelToken=${CHANNEL_TOKEN}","-Dline.bot.channelSecret=${CHANNEL_SECRET}","./target/line-echo-robot-0.1.jar"]