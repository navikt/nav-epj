FROM gcr.io/distroless/java25-debian13@sha256:ed234e7af410c54dea28376730b472611c93c67c62869506cdaa7321ddb67c37

WORKDIR /app

COPY build/libs/nav-epj-all.jar app.jar

ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"

EXPOSE 8080

CMD [ "app.jar" ]
