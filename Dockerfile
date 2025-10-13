FROM gcr.io/distroless/nodejs24-debian12@sha256:5026fedf05325da8b8d6ff4d3618c73e40e4113748af9fdc209c1050c4e884e4

WORKDIR /app

COPY next-logger.config.mjs /app/
COPY .next/standalone /app/
COPY public /app/public

EXPOSE 3000

ENV NODE_ENV=production

CMD ["server.js"]
