FROM gcr.io/distroless/nodejs22-debian12@sha256:f71f4b7976f952df9c72b4d2ce82e09f0f57d398a25c0c3ebd63557e973f1ee7

WORKDIR /app

ENV NODE_ENV=production

COPY next-logger.config.mjs /app/
COPY .next/standalone /app/

EXPOSE 3000

ENV PORT=3000

CMD ["server.js"]