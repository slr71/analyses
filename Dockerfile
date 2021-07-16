FROM clojure:openjdk-17-lein-alpine

WORKDIR /usr/src/app

RUN apk add --no-cache git

CMD ["--help"]

COPY conf/main/logback.xml /usr/src/app/

COPY project.clj /usr/src/app/
RUN lein deps

RUN ln -s "/opt/openjdk-17/bin/java" "/bin/analyses"

COPY . /usr/src/app

RUN lein uberjar && \
    cp target/analyses-0.1.0-SNAPSHOT-standalone.jar .

ENTRYPOINT ["analyses", "-Dlogback.configurationFile=/usr/src/app/logback.xml", "-cp", ".:analyses-0.1.0-SNAPSHOT-standalone.jar:/", "analyses.core"]

ARG git_commit=unknown
ARG version=unknown
ARG descriptive_version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
LABEL org.cyverse.descriptive-version="$descriptive_version"
LABEL org.label-schema.vcs-ref="$git_commit"
LABEL org.label-schema.vcs-url="https://github.com/cyverse-de/analyses"
LABEL org.label-schema.version="$descriptive_version"
