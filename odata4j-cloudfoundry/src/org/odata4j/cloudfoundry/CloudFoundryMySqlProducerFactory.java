package org.odata4j.cloudfoundry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.eclipse.persistence.config.EntityManagerProperties;
import org.odata4j.producer.ODataProducer;
import org.odata4j.producer.ODataProducerFactory;
import org.odata4j.producer.jpa.JPAProducer;

public class CloudFoundryMySqlProducerFactory implements ODataProducerFactory {
  
  @Override
  public ODataProducer create(Properties properties) {

    String persistenceUnitName = "NorthwindServiceEclipseLink";
    String edmNamespace = "Northwind";
    int maxResults = 20;

    // access mysql connection info from VCAP_SERVICES environment variable (json payload)
    String vcapServices = System.getenv("VCAP_SERVICES");
    Matcher m = Pattern.compile(".*hostname\":\"(.*?)\",\"port\":(.*?),\"password\":\"(.*?)\",\"name\":\"(.*?)\",\"user\":\"(.*?)\".*", Pattern.DOTALL).matcher(vcapServices);
    if (m.matches()) {
      String mysqlHost = m.group(1);
      String mysqlPort = m.group(2);
      String mysqlPassword = m.group(3);
      String mysqlName = m.group(4);
      String mysqlUser = m.group(5);
      String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s", mysqlHost, mysqlPort, mysqlName);

      properties.setProperty(EntityManagerProperties.JDBC_USER, mysqlUser);
      properties.setProperty(EntityManagerProperties.JDBC_PASSWORD, mysqlPassword);
      properties.setProperty(EntityManagerProperties.JDBC_URL, jdbcUrl);
      EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnitName, properties);
      emf.createEntityManager();  // force connection - will drop and recreate tables
      fillDatabase(jdbcUrl, mysqlUser, mysqlPassword);  // populate initial data
      return new JPAProducer(emf, edmNamespace, maxResults);
    } else {
      throw new IllegalStateException("Unable to parse mysql info from VCAP_SERVICES: " + vcapServices);
    }
  }

  private static void fillDatabase(String jdbcUrl, String user, String password) {
    Connection conn = null;
    String line = "";
    try {
      Class.forName("com.mysql.jdbc.Driver");
      conn = DriverManager.getConnection(jdbcUrl, user, password);
      Statement statement = conn.createStatement();
      InputStream sql = CloudFoundryMySqlProducerFactory.class.getResourceAsStream("/META-INF/northwind_insert.sql");
      BufferedReader br = new BufferedReader(new InputStreamReader(sql, "UTF-16"));
      while ((line = br.readLine()) != null) {
        line = line.replace("`", "");
        line = line.replace(");", ")");
        line = line.replace("'0x", "'");
        if (line.length() > 5) {
          statement.executeUpdate(line);
        }
      }
      br.close();
      statement.close();

    } catch (Exception ex) {
      throw new RuntimeException(ex);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

}
