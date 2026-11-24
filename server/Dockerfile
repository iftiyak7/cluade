# syntax=docker/dockerfile:1
# Build context: repository root (needs server/ and website/).
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY server/go.mod server/go.sum ./
RUN go mod download
COPY server/ ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/hayat-server .

FROM alpine:3.20 AS site
COPY website/ /site/
RUN rm -f /site/build.py /site/site.json /site/README.md && find /site -name '*.png' ! -name 'en-home.png' ! -name 'bn-home.png' -delete

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/hayat-server /app/hayat-server
COPY --from=site /site /app/site
ENV HAYAT_ADDR=0.0.0.0:8080 HAYAT_SITE_DIR=/app/site HAYAT_TRUST_PROXY=1
USER nonroot:nonroot
EXPOSE 8080
HEALTHCHECK NONE
ENTRYPOINT ["/app/hayat-server"]
CMD ["serve"]
