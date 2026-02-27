#!/usr/bin/env bash
set -e

GREEN="\033[0;32m"
CYAN="\033[0;36m"
BOLD="\033[1m"
RED="\033[1;31m"
RESET="\033[0m"

running_services="$(
  docker compose ps --format json 2>/dev/null \
  | jq -r 'select(.State=="running") | .Service' \
  | sort -u
)"

if echo "$running_services" | grep -qx "valkey"; then
  echo -e "${GREEN}✔ Valkey service is already running in Docker Compose${RESET}"
  exit 0
fi

echo -e "${CYAN}Starting Valkey service in Docker Compose…${RESET}"

# Suppress the ugly compose chatter unless it fails
output=$(docker compose up -d --remove-orphans 2>&1)
if [ $? -eq 0 ]; then
  echo -e "${GREEN}${BOLD}✔ Compose started!${RESET}"
else
  echo -e "${RED}${BOLD}✖ Docker compose failed to start${RESET}"
  echo "$output"
  exit 1
fi
