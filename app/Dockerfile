FROM ghcr.io/cirruslabs/flutter:stable AS build
WORKDIR /app
COPY pubspec.yaml ./
RUN flutter pub get
COPY . .
# Gera as pastas de plataforma que faltarem (não sobrescreve lib/, web/manifest.json etc.)
RUN flutter create . --platforms=web --project-name autoescola_app --org br.com.cfc \
 && flutter build web --release

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build/web /usr/share/nginx/html
EXPOSE 80
