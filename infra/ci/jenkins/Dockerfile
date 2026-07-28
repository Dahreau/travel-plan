FROM jenkins/jenkins:lts-jdk21

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER root
RUN mkdir -p /var/jenkins_home/.m2 && chown -R jenkins:jenkins /var/jenkins_home/.m2
USER jenkins

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/var/jenkins_home/casc.yaml"
