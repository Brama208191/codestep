FROM eclipse-temurin:21-jdk-alpine
# socat and su-exec are only used by judged.sh, the single-container judge service.
RUN apk add --no-cache socat su-exec \
    && adduser -D -H -u 61000 -s /sbin/nologin judge
COPY TestRunner.java /opt/runner/TestRunner.java
RUN javac -d /opt/runner /opt/runner/TestRunner.java && rm /opt/runner/TestRunner.java
COPY runner.policy /opt/runner/runner.policy
COPY run.sh judged.sh /usr/local/bin/
RUN sed -i 's/\r$//' /usr/local/bin/run.sh /usr/local/bin/judged.sh /opt/runner/runner.policy \
    && chmod 755 /usr/local/bin/run.sh /usr/local/bin/judged.sh && chmod 644 /opt/runner/*
WORKDIR /tmp
ENTRYPOINT ["/usr/local/bin/run.sh"]
