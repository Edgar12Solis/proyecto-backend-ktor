# Fase 1: Compilar la aplicación
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .

# Usamos 'build -x test' que ya sabemos que compila todo tu proyecto a la perfección
RUN bash gradlew build -x test --no-daemon -Dorg.gradle.jvmargs="-Xmx256m -XX:MaxMetaspaceSize=128m"

# Truco de Linux: Buscamos el jar pesado generado y lo dejamos listo en la raíz
RUN mkdir -p /app/ready && (cp build/libs/*-all.jar /app/ready/app.jar || cp build/libs/*.jar /app/ready/app.jar)

# Fase 2: Ejecutar la app con el mínimo de RAM posible
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copiamos el archivo app.jar directo y sin asteriscos problemáticos
COPY --from=build /app/ready/app.jar app.jar
EXPOSE 8080

# Comando para encender la app usando solo 192MB de RAM
CMD ["java", "-Xmx192m", "-jar", "app.jar"]