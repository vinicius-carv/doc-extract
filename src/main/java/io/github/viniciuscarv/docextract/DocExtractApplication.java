package io.github.viniciuscarv.docextract;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DocExtractApplication {

  public static void main(String[] args) {
    boolean eval = Arrays.asList(args).contains("--eval");
    if (eval) {
      System.setProperty("spring.main.web-application-type", "none");
    }
    if (Arrays.asList(args).contains("--record")) {
      System.setProperty("docextract.record", "true");
    }
    ConfigurableApplicationContext ctx = SpringApplication.run(DocExtractApplication.class, args);
    if (eval) {
      System.exit(SpringApplication.exit(ctx)); // EvalRunner is an ExitCodeGenerator bean
    }
  }
}
