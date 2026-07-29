FROM jenkins/jenkins:lts-jdk21

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER root
RUN mkdir -p /var/jenkins_home/.m2 && chown -R jenkins:jenkins /var/jenkins_home/.m2

# Node.js pour builder/tester frontend/ (Angular) — même logique que le JDK déjà
# présent pour mvnw : un runtime installé directement sur l'agent, pas de conteneur
# jetable (Jenkins n'a pas accès à Docker aujourd'hui, voir 01-ci-cd.md).
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

USER jenkins

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/var/jenkins_home/casc.yaml"
