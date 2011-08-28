package org.odata4j.heroku;

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

public class HerokuPostgresProducerFactory implements ODataProducerFactory {
  
  @Override
  public ODataProducer create(Properties properties) {
    String persistenceUnitName = "NorthwindServiceEclipseLink";
    String edmNamespace = "Northwind";
    int maxResults = 20;

    // access postgres connection info from SHARED_DATABASE_URL environment variable
    String sharedDatabaseUrl = System.getenv("SHARED_DATABASE_URL");
    Matcher m = Pattern.compile("postgres://(.+):(.+)@(.+)/(.+)").matcher(sharedDatabaseUrl);
    if (m.matches()) {
      String postgresUser = m.group(1);
      String postgresPassword = m.group(2);
      String postgresHost = m.group(3);
      String postgresName = m.group(4);
      String jdbcUrl = String.format("jdbc:postgresql://%s/%s", postgresHost, postgresName);

      properties.setProperty(EntityManagerProperties.JDBC_USER, postgresUser);
      properties.setProperty(EntityManagerProperties.JDBC_PASSWORD, postgresPassword);
      properties.setProperty(EntityManagerProperties.JDBC_URL, jdbcUrl);
      EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnitName, properties);
      emf.createEntityManager();  // force connection - will drop and recreate tables
      fillDatabase(jdbcUrl, postgresUser, postgresPassword);  // populate initial data
      return new JPAProducer(emf, edmNamespace, maxResults);
    } else {
      throw new IllegalStateException("Unable to parse postgres info from SHARED_DATABASE_URL: " + sharedDatabaseUrl);
    }  
  }

  private static void fillDatabase(String jdbcUrl, String user, String password) {
    Connection conn = null;
    String line = "";
    try {
      Class.forName("org.postgresql.Driver");
      conn = DriverManager.getConnection(jdbcUrl, user, password);
      Statement statement = conn.createStatement();
      InputStream sql = HerokuPostgresProducerFactory.class.getResourceAsStream("/META-INF/northwind_insert.sql");
      BufferedReader br = new BufferedReader(new InputStreamReader(sql, "UTF-16"));
      while ((line = br.readLine()) != null) {
        line = line.replace("`", "");
        line = line.replace(");", ")");
        line = line.replace("'0x", "'");

        // postgres-specific workaround for boolean literals
        if (line.contains("INSERT INTO Products(")) {
          line = line.replace(",1)", ",'1')").replace(",0)", ",'0')");
        }

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
