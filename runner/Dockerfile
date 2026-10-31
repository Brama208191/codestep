FROM eclipse-temurin:21-jdk-alpine
COPY TestRunner.java /opt/runner/TestRunner.java
RUN javac -d /opt/runner /opt/runner/TestRunner.java && rm /opt/runner/TestRunner.java
COPY run.sh /usr/local/bin/run.sh
RUN sed -i 's/\r$//' /usr/local/bin/run.sh && chmod 755 /usr/local/bin/run.sh
WORKDIR /tmp
ENTRYPOINT ["/usr/local/bin/run.sh"]
