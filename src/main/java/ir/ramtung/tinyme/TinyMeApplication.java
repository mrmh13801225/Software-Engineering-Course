package ir.ramtung.tinyme;

import ir.ramtung.tinyme.config.Configurations;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@Import(Configurations.class)
@EnableJms
public class TinyMeApplication {

	public static void main(String[] args) {
		SpringApplication.run(TinyMeApplication.class, args);
	}
}
