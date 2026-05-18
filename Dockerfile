FROM gcr.io/distroless/java21-debian12@sha256:5732983ef98fcde2b19a24d0d3b5f46644f059d339740e024f29a169eb8a2a65

WORKDIR /app

COPY build/libs/epj-all.jar app.jar

ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"

EXPOSE 8080

CMD [ "app.jar" ]
