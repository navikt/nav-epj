FROM gcr.io/distroless/java25-debian13@sha256:aea9b7c0ab3605df2a08bb71e865c9ddb5dcd00c25625605acb5d30bda1c1486

WORKDIR /app

COPY build/libs/nav-epj-all.jar app.jar

ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"

EXPOSE 8080

CMD [ "app.jar" ]
