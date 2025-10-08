FROM gcr.io/distroless/nodejs24-debian12@sha256:98c1d2e0ebf042776341cb47b5eac60c7b970ec955e365ef2454f92875bf0320

WORKDIR /app

COPY next-logger.config.mjs /app/
COPY .next/standalone /app/
COPY public /app/public

EXPOSE 3000

ENV NODE_ENV=production

CMD ["server.js"]
