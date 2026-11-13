FROM maven:3.9-eclipse-temurin-21 AS dependencies

WORKDIR /build
COPY pom.xml ./
RUN mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/build/lib

FROM tomcat:9.0-jdk21-temurin

WORKDIR /opt/bus

COPY --from=dependencies /build/lib ./lib
COPY StandaloneBusServer.java ./
COPY Task3_HTML_Servlet ./Task3_HTML_Servlet
COPY Task4_JDBC_Servlet ./Task4_JDBC_Servlet
COPY tomcat-src ./tomcat-src

RUN rm -rf /usr/local/tomcat/webapps/* \
    && mkdir -p /usr/local/tomcat/webapps/ROOT/WEB-INF/classes \
                /usr/local/tomcat/webapps/task3/WEB-INF/classes \
                /usr/local/tomcat/webapps/task4/WEB-INF/classes \
                /usr/local/tomcat/webapps/task4/WEB-INF/lib \
    && cp tomcat-src/ROOT-web.xml /usr/local/tomcat/webapps/ROOT/WEB-INF/web.xml \
    && cp Task3_HTML_Servlet/WEB-INF/web.xml /usr/local/tomcat/webapps/task3/WEB-INF/web.xml \
    && cp Task4_JDBC_Servlet/WEB-INF/web.xml /usr/local/tomcat/webapps/task4/WEB-INF/web.xml \
    && cp Task3_HTML_Servlet/*.html /usr/local/tomcat/webapps/task3/ \
    && cp Task4_JDBC_Servlet/*.html /usr/local/tomcat/webapps/task4/ \
    && cp lib/* /usr/local/tomcat/webapps/task4/WEB-INF/lib/ \
    && javac -cp "/opt/bus/lib/*" StandaloneBusServer.java \
    && javac -cp "/usr/local/tomcat/lib/servlet-api.jar" -d /usr/local/tomcat/webapps/ROOT/WEB-INF/classes tomcat-src/ApiProxyServlet.java \
    && javac -cp "/usr/local/tomcat/lib/servlet-api.jar" -d /usr/local/tomcat/webapps/task3/WEB-INF/classes Task3_HTML_Servlet/src/*.java \
    && javac -cp "/usr/local/tomcat/lib/servlet-api.jar:/opt/bus/lib/*" -d /usr/local/tomcat/webapps/task4/WEB-INF/classes Task4_JDBC_Servlet/src/*.java

ENV INTERNAL_PORT=10001

CMD ["sh", "-c", "java -cp '/opt/bus:/opt/bus/lib/*' StandaloneBusServer & sed -i \"s/port=\\\"8080\\\"/port=\\\"${PORT:-10000}\\\"/\" /usr/local/tomcat/conf/server.xml && catalina.sh run"]
