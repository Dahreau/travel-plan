FROM docker:27-cli AS docker-cli

FROM docker/compose-bin:v5.3.1 AS compose-bin

FROM jenkins/jenkins:lts-jdk21

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

USER root
COPY --from=compose-bin /docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
RUN chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
USER jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/var/jenkins_home/casc.yaml"
