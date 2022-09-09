FROM registry.access.redhat.com/ubi8/openjdk-11:1.11

WORKDIR /work/
RUN chown :root /work \
    && chmod "g+rwX" /work \
    && chown :root /work

COPY target/*-runner.jar /work/application.jar
# COPY target/lib/* /work/lib/

ARG BRANCH
ARG COMMIT
ARG VERSION
ARG TIMESTAMP
ENV BRANCH=$BRANCH
ENV TIMESTAMP=$TIMESTAMP
ENV COMMIT=$COMMIT
ENV VERSION=$VERSION

EXPOSE 8080

CMD ["java","-jar","application.jar","-Dquarkus.http.host=0.0.0.0"]
