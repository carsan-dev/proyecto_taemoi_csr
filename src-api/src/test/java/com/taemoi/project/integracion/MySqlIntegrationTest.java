package com.taemoi.project.integracion;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de integración contra un MySQL real (Testcontainers).
 *
 * <p>El contenedor es un singleton estático: se arranca una sola vez por JVM y lo
 * comparten todas las clases que heredan de esta base (Ryuk lo elimina al terminar).
 * Spring obtiene la conexión mediante {@link ServiceConnection}, por lo que sirve
 * tanto para {@code @SpringBootTest} como para slices como {@code @DataJpaTest}
 * (en ese caso con {@code @AutoConfigureTestDatabase(replace = NONE)}).
 *
 * <p>Sin Docker disponible, las clases se omiten ({@code disabledWithoutDocker}).
 * La configuración ficticia vive en {@code application-integracion.properties}.
 */
@Tag("integracion")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integracion")
public abstract class MySqlIntegrationTest {

	/** Misma versión mayor que producción (docker-compose.production.yml, mysql/Dockerfile). */
	public static final String MYSQL_IMAGE = "mysql:8.0";

	@ServiceConnection
	protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
			.withDatabaseName("taemoi_it")
			.withUsername("taemoi_it")
			.withPassword("taemoi_it")
			// Datos en memoria y sin durabilidad: el contenedor es desechable, solo importa la velocidad.
			.withTmpFs(Map.of("/var/lib/mysql", "rw"))
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
					"--innodb_flush_log_at_trx_commit=0", "--sync_binlog=0", "--skip-log-bin",
					"--innodb_doublewrite=0", "--innodb_flush_method=nosync");

	static {
		MYSQL.start();
	}
}
