FROM clojure:temurin-21-lein-alpine

WORKDIR /usr/src/app

RUN apk add --no-cache git

CMD ["--help"]

COPY conf/main/logback.xml /usr/src/app/

COPY project.clj /usr/src/app/
RUN lein deps

RUN ln -s "/opt/java/openjdk/bin/java" "/bin/analyses"

ENV OTEL_TRACES_EXPORTER none

COPY . /usr/src/app

RUN lein uberjar && \
    cp target/analyses-standalone.jar .

ENTRYPOINT ["analyses", "-Dlogback.configurationFile=/usr/src/app/logback.xml", "-javaagent:/usr/src/app/opentelemetry-javaagent.jar", "-Dotel.resource.attributes=service.name=analyses", "-cp", ".:analyses-standalone.jar:/", "analyses.core"]

ARG git_commit=unknown
ARG version=unknown
ARG descriptive_version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
LABEL org.cyverse.descriptive-version="$descriptive_version"
LABEL org.label-schema.vcs-ref="$git_commit"
LABEL org.label-schema.vcs-url="https://github.com/cyverse-de/analyses"
LABEL org.label-schema.version="$descriptive_version"
