FROM jenkins/jenkins:lts-jdk21

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER root
RUN mkdir -p /var/jenkins_home/.m2 && chown -R jenkins:jenkins /var/jenkins_home/.m2

# docker-cli (client seul, pas docker.io/containerd) pour que le stage Deploy
# pilote le démon Docker de l'hôte via le socket monté (voir
# infra/ci/docker-compose.yml). docker.io tire aussi containerd/runc/iptables
# et ne fournit /usr/bin/docker que via un Recommends, exclu par
# --no-install-recommends : docker-cli l'a en Depends direct.
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
  && apt-get install -y --no-install-recommends nodejs docker-cli python3-pip sudo \
  && rm -rf /var/lib/apt/lists/* \
  && pip install --break-system-packages ansible \
  && ansible-galaxy collection install community.docker

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/var/jenkins_home/casc.yaml"
