FROM gcr.io/distroless/nodejs22-debian12@sha256:7cdb3a67f094f9702f34bcce19925cb38571168adaa57d3ca455b55708df67e1

WORKDIR /app

COPY next-logger.config.mjs /app/
COPY .next/standalone /app/
COPY public /app/public

EXPOSE 3000

ENV NODE_ENV=production

CMD ["server.js"]
