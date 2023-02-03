FROM openjdk:11
COPY ./target/line-echo-robot-0.1.jar ./
ENTRYPOINT ["java","-jar","line-echo-robot-0.1.jar"]