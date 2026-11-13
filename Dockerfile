FROM maven:3.9-eclipse-temurin-21 AS dependencies

WORKDIR /build
COPY pom.xml ./
RUN mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/build/lib

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=dependencies /build/lib ./lib
COPY StandaloneBusServer.java ./
COPY Task3_HTML_Servlet ./Task3_HTML_Servlet
COPY Task4_JDBC_Servlet ./Task4_JDBC_Servlet

RUN javac -cp "/app/lib/*" StandaloneBusServer.java

CMD ["sh", "-c", "java -cp '.:/app/lib/*' StandaloneBusServer"]
