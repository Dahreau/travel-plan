FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system spring && adduser --system --ingroup spring spring
# Spring Cloud Gateway Server MVC construit en interne son propre client HTTP pour
# proxyfier les requetes, et ignore aussi bien spring.http.client.ssl.bundle qu'un bean
# RestClient personnalise (verifie empiriquement sur les deux). Plutot que de continuer
# a deviner quel mecanisme Spring utilise reellement, on importe le certificat directement
# dans le keystore de confiance du JDK : ainsi n'importe quel client HTTP de cette JVM, quel
# qu'il soit, fait confiance a ce certificat precis, sans dependre de la configuration Spring.
COPY --from=internal-tls-certs internal.crt /tmp/internal.crt
RUN keytool -importcert -noprompt -trustcacerts -alias travel-plan-internal \
    -file /tmp/internal.crt -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && rm /tmp/internal.crt
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
