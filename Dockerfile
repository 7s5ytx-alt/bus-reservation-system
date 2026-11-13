FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY StandaloneBusServer.java ./
COPY Task3_HTML_Servlet ./Task3_HTML_Servlet
COPY Task4_JDBC_Servlet ./Task4_JDBC_Servlet

RUN javac StandaloneBusServer.java

CMD ["java", "StandaloneBusServer"]
