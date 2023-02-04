FROM openkbs/jdk11-mvn-py3
COPY ./ ./
CMD mvn clean package -DskipTests
ARG CHANNEL_TOKEN
ARG CHANNEL_SECRET
CMD echo CHANNEL_TOKEN
CMD echo %CHANNEL_TOKEN%
CMD echo CHANNEL_SECRET
CMD echo %CHANNEL_SECRET%
CMD ls
CMD java -Dline.bot.channelToken=${CHANNEL_TOKEN} -Dline.bot.channelSecret=${CHANNEL_SECRET} -jar ./target/line-echo-robot-0.1.jar