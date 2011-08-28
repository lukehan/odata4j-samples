package org.odata4j.heroku;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;
import com.sun.jersey.spi.container.servlet.*;

public class App {

  public static void main(String[] args) throws Exception {
    Server server = new Server(Integer.valueOf(System.getenv("PORT")));
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);
    
    // Servlet 1: Expose an OData service endpoint (Northwind DB using JPA producer over shared Postgres)
    ServletHolder servlet1 = new ServletHolder(new ServletContainer());
    servlet1.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "org.odata4j.producer.resources.ODataResourceConfig");
    servlet1.setInitParameter("odata4j.producerfactory", "org.odata4j.heroku.HerokuPostgresProducerFactory");
    context.addServlet(servlet1, "/northwind.svc/*");

    // Servlet 2: Expose another OData service endpoint (In-memory producer example)
    ServletHolder servlet2 = new ServletHolder(new ServletContainer());
    servlet2.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "org.odata4j.producer.resources.ODataResourceConfig");
    servlet2.setInitParameter("odata4j.producerfactory", "org.odata4j.heroku.HerokuInMemoryProducerFactory");
    context.addServlet(servlet2, "/inmemory.svc/*");

    // Servlet 3: Enable crossdomain access for browser clients
    ServletHolder servlet3 = new ServletHolder(new ServletContainer());
    servlet3.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "org.odata4j.producer.resources.CrossDomainResourceConfig");
    context.addServlet(servlet3, "/*");
    
    server.start();
    server.join();   
  }
  
}
