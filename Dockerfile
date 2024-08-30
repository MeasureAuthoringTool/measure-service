## Use chainguard dev base image can be used for local devs
FROM cgr.dev/chainguard/jdk:latest-dev AS build_dev
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

USER root
RUN apk add coreutils curl

## Add the wait script to the image
COPY --from=ghcr.io/ufoscout/docker-compose-wait:latest /wait /wait

## Download new relic java agent
RUN curl -O https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.jar \
    && curl -O https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.yml
    

ENTRYPOINT ["java","-jar","-Dspring.profiles.active=it", "app.jar"]


FROM cgr.dev/chainguard/jdk:latest AS prod

ARG JAR_FILE=target/*.jar
COPY --chown=java:java ${JAR_FILE} app.jar

COPY --from=ghcr.io/ufoscout/docker-compose-wait:latest --chown=java:java /wait /wait
COPY --from=build_dev --chown=java:java /home/build/newrelic.jar ./newrelic.jar
COPY --from=build_dev --chown=java:java /home/build/newrelic.yml ./newrelic.yml

## Launch the wait tool and then your application
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=it", "app.jar"]
