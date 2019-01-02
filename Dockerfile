### BUILD STEP ###
FROM openjdk:8u181-jdk-stretch as builder
ENV STAGE dev

WORKDIR /build
ADD . /build
RUN ./docker/build.sh

#### RUN STEP ###
FROM openjdk:8u181-jre-slim-stretch
ENV HTTP_PORT 9200
ENV ADMIN_PORT 0
ENV STAGE dev

WORKDIR /app
COPY --from=builder /build/target/universal/stage .
COPY ./docker/install_run_deps.sh .
COPY ./docker/run.sh .
RUN ./install_run_deps.sh && rm -f install_run_deps.sh

CMD ["/app/run.sh"]
