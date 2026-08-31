# =============================================================================
# La imagen del backend para ECS Fargate.
#
# Una sola etapa, a proposito: el jar ya lo construyo y lo probo Maven
# (backend.yml corre "mvn verify" antes de llegar aqui). Compilar otra vez
# dentro de Docker duplicaria ese trabajo y haria la imagen mucho mas lenta de
# construir, sin ganar nada. Por eso este Dockerfile EXIGE que target/
# backend.jar exista:
#
#     mvn clean verify && docker build -t backend .
# =============================================================================

# Corretto 21 y no 25: la misma version que declara el pom.xml. Y desde ECR
# Public y no desde Docker Hub, que limita las descargas anonimas por IP: una
# sala con 40 personas construyendo a la vez recibe 429 y la clase se detiene.
#
# La variante headless no trae las librerias graficas del JDK, que un servicio
# HTTP no usa jamas: pesa bastante menos, y eso es menos que subir a ECR y menos
# que Fargate baja en cada arranque. (No hay tag alpine en este repositorio.)
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21-al2023-headless

WORKDIR /app

# El nombre del jar viene del <finalName> del pom, igual que en el Procfile de
# Beanstalk: no lleva version, asi que esta linea no cambia entre releases.
COPY target/backend.jar app.jar

# No correr como root. Fargate no lo exige, pero si alguien logra ejecutar algo
# dentro del contenedor, que no sea con todos los permisos.
#
# Se usa el uid 65534 (nobody), que la imagen base ya trae, en vez de crear un
# usuario con useradd: eso obligaria a instalar shadow-utils y esa sola capa
# agregaba ~170 MB a la imagen. El jar queda legible para todos (644), que es
# lo unico que hace falta para arrancarlo.
USER 65534:65534

# Dentro de un contenedor la JVM ve la memoria del contenedor, pero por defecto
# reserva solo una fraccion. Sin esto, buena parte de la memoria que Fargate te
# cobra queda sin usar.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# 8080 es el puerto por defecto de Spring: aqui no hay nginx delante, asi que
# no existe la trampa del 5000 que tenia Beanstalk.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
