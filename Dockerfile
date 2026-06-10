# Fase 1: Compilar la aplicación de forma ligera
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
# ¡AQUÍ ESTÁ EL CAMBIO! Agregamos "bash" antes de gradlew
RUN bash gradlew shadowJar --no-daemon -Dorg.gradle.jvmargs="-Xmx256m -XX:MaxMetaspaceSize=128m"

# Fase 2: Ejecutar la app con el mínimo de RAM posible
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080

# Comando para encender la app usando solo 192MB de RAM
CMD ["java", "-Xmx192m", "-jar", "app.jar"]