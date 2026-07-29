FROM jenkins/jenkins:lts-jdk21

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER root
RUN mkdir -p /var/jenkins_home/.m2 && chown -R jenkins:jenkins /var/jenkins_home/.m2

RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
  && apt-get install -y --no-install-recommends nodejs docker.io python3-pip sudo \
  && rm -rf /var/lib/apt/lists/* \
  && pip install --break-system-packages ansible \
  && ansible-galaxy collection install community.docker

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/var/jenkins_home/casc.yaml"
