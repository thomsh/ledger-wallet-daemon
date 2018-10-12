### BUILD STEP ###
FROM openjdk:8u181 as builder
ENV STAGE dev

WORKDIR /build
ADD . /build
RUN ./docker/build.sh

#### RUN STEP ###
FROM debian:stretch-slim
ENV JDK_DEBIAN_VERSION=8u181-b13-1~deb9u1
ENV HTTP_PORT 9200
ENV ADMIN_PORT 0
ENV STAGE dev

WORKDIR /app
COPY --from=builder /build/daemon/target/universal/stage .
COPY ./docker/install_run_deps.sh .
COPY ./docker/run.sh .
RUN ./install_run_deps.sh && rm -f install_run_deps.sh

CMD ["/app/run.sh"]
