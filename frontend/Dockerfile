# Build stage
FROM node:20-slim AS build
WORKDIR /app

COPY package*.json ./
RUN npm install --legacy-peer-deps #ci

COPY . .
ENV NEXT_PUBLIC_API_URL=
RUN npm run build

# Production stage with Nginx
FROM nginx:alpine
COPY --from=build /app/out /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
