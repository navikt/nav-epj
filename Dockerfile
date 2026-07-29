FROM gcr.io/distroless/java25-debian13@sha256:7bd38f96c69b64b0889cee9e249288189aaa28d4998385e21d29fa8d34ef38c4

WORKDIR /app

COPY build/libs/nav-epj-all.jar app.jar

ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"

EXPOSE 8080

CMD [ "app.jar" ]
