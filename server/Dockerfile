FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY src ./src
ENV NODE_ENV=production PORT=8080 DB_PATH=/data/receiptbook.db
VOLUME /data
EXPOSE 8080
USER node
CMD ["node", "src/index.js"]
