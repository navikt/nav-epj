FROM gcr.io/distroless/java25-debian13@sha256:d000221060080b5691b1521d3113f701bd3e0041c6b792a00cf9006d8d3b4ec0

WORKDIR /app

COPY build/libs/nav-epj-all.jar app.jar

ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"

EXPOSE 8080

CMD [ "app.jar" ]
