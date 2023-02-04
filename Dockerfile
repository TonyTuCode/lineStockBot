FROM openkbs/jdk11-mvn-py3
COPY ./ ./
CMD mvn clean package -DskipTests
ENV CHANNEL_TOKEN
ENV CHANNEL_SECRET
CMD ls
CMD java -Dline.bot.channelToken=${CHANNEL_TOKEN} -Dline.bot.channelSecret=${CHANNEL_SECRET} -jar ./target/line-echo-robot-0.1.jar