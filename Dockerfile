FROM gcr.io/distroless/java25-debian13@sha256:8ce26d023018ca2f11bf2530cd3a10a7fd8456c3142b5f9a7d6b135a1411c86a

WORKDIR /app

COPY build/libs/nav-epj-all.jar app.jar

ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"

EXPOSE 8080

CMD [ "app.jar" ]
