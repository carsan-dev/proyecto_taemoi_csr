
DROP SCHEMA IF EXISTS `taemoidb`;
CREATE SCHEMA IF NOT EXISTS `taemoidb`;
USE `taemoidb`;

CREATE TABLE IF NOT EXISTS `taemoidb`.`imagen` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `datos` LONGBLOB NOT NULL,
  `nombre` VARCHAR(200) NOT NULL,
  `tipo` VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`categoria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `nombre` VARCHAR(255) NOT NULL,
  `tipo_categoria` ENUM('CADETE', 'INFANTIL', 'JUNIOR', 'PRECADETE', 'PRETKD', 'SENIOR', 'SUB21') NULL DEFAULT NULL
  );

CREATE TABLE IF NOT EXISTS `taemoidb`.`grado` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `tipo_grado` ENUM('AMARILLO', 'AMARILLO_NARANJA', 'AZUL', 'AZUL_ROJO', 'BLANCO', 'BLANCO_AMARILLO', 'NARANJA', 'NARANJA_VERDE', 'NEGRO', 'ROJO', 'ROJO_NEGRO', 'VERDE', 'VERDE_AZUL') NOT NULL
   );

CREATE TABLE IF NOT EXISTS `taemoidb`.`alumno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `apellidos` VARCHAR(255) NOT NULL,
  `cuantia_tarifa` DOUBLE NOT NULL,
  `direccion` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NULL DEFAULT NULL,
  `fecha_alta` DATE NULL DEFAULT NULL,
  `fecha_baja` DATE NULL DEFAULT NULL,
  `fecha_nacimiento` DATE NOT NULL,
  `nif` VARCHAR(9) NOT NULL,
  `nombre` VARCHAR(255) NOT NULL,
  `numero_expediente` INT NOT NULL,
  `telefono` INT NOT NULL,
  `tipo_tarifa` ENUM('ADULTO', 'ADULTO_GRUPO', 'FAMILIAR', 'HERMANOS', 'INFANTIL', 'INFANTIL_GRUPO', 'PADRES_HIJOS') NOT NULL,
  `categoria_id` BIGINT NULL DEFAULT NULL,
  `foto_alumno_id` BIGINT NULL DEFAULT NULL,
  `grado_id` BIGINT NULL DEFAULT NULL,
  UNIQUE INDEX `UKi2otd4eccxkj7jwqeygr3wgoj` (`numero_expediente` ASC) VISIBLE,
  UNIQUE INDEX `UKjuke9njf11mdvhyauigofa3g2` (`foto_alumno_id` ASC) VISIBLE,
  INDEX `FK3sss574xtkjpgdljygxm19glf` (`categoria_id` ASC) VISIBLE,
  INDEX `FKid01ntlqpypy38pi2bk0nlof3` (`grado_id` ASC) VISIBLE,
  CONSTRAINT `FK35h62thngrq0o15s2www7u451`
    FOREIGN KEY (`foto_alumno_id`)
    REFERENCES `taemoidb`.`imagen` (`id`),
  CONSTRAINT `FK3sss574xtkjpgdljygxm19glf`
    FOREIGN KEY (`categoria_id`)
    REFERENCES `taemoidb`.`categoria` (`id`),
  CONSTRAINT `FKid01ntlqpypy38pi2bk0nlof3`
    FOREIGN KEY (`grado_id`)
    REFERENCES `taemoidb`.`grado` (`id`));

CREATE TABLE IF NOT EXISTS `taemoidb`.`grupo` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `nombre` VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`alumno_grupo` (
  `grupo_id` BIGINT NOT NULL,
  `alumno_id` BIGINT NOT NULL,
  INDEX `FK9m58u5wqf6fc2i5qur535qic6` (`alumno_id` ASC) VISIBLE,
  INDEX `FKc3q87xf5n31xgpfun4b7jndg0` (`grupo_id` ASC) VISIBLE,
  CONSTRAINT `FK9m58u5wqf6fc2i5qur535qic6`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoidb`.`alumno` (`id`),
  CONSTRAINT `FKc3q87xf5n31xgpfun4b7jndg0`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `taemoidb`.`grupo` (`id`)
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`evento` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `descripcion` VARCHAR(500) NOT NULL,
  `titulo` VARCHAR(100) NOT NULL,
  `foto_evento_id` BIGINT NULL DEFAULT NULL,
  UNIQUE INDEX `UK4181ienduxeq2ridhuwd3qvwk` (`foto_evento_id` ASC) VISIBLE,
  CONSTRAINT `FK8r4xhbhw72x5gsalwsmf6km67`
    FOREIGN KEY (`foto_evento_id`)
    REFERENCES `taemoidb`.`imagen` (`id`)
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`examen` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `fecha` DATE NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  `examen_grado_id` BIGINT NULL DEFAULT NULL,
  `grado_id` BIGINT NULL DEFAULT NULL,
  INDEX `FKfpng0mexxc3pejhtus8lvn65o` (`alumno_id` ASC) VISIBLE,
  INDEX `FKg7lpgnjshqpi2ovn0qw1moo3m` (`examen_grado_id` ASC) VISIBLE,
  INDEX `FKgjt7f8vr2l4syxewdxjia50c0` (`grado_id` ASC) VISIBLE,
  CONSTRAINT `FKfpng0mexxc3pejhtus8lvn65o`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoidb`.`alumno` (`id`),
  CONSTRAINT `FKg7lpgnjshqpi2ovn0qw1moo3m`
    FOREIGN KEY (`examen_grado_id`)
    REFERENCES `taemoidb`.`grado` (`id`),
  CONSTRAINT `FKgjt7f8vr2l4syxewdxjia50c0`
    FOREIGN KEY (`grado_id`)
    REFERENCES `taemoidb`.`grado` (`id`)
    )
;

CREATE TABLE IF NOT EXISTS `taemoidb`.`pago` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `concepto` VARCHAR(255) NOT NULL,
  `cuantia` DOUBLE NOT NULL,
  `estado` ENUM('NO_PAGADO', 'PAGADO') NOT NULL,
  `fecha` DATE NOT NULL,
  `numero_expediente_alumno` VARCHAR(255) NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  INDEX `FKaxf1p403r03amcvwbacx9rusq` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FKaxf1p403r03amcvwbacx9rusq`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoidb`.`alumno` (`id`)
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`turno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `dia_semana` VARCHAR(255) NOT NULL,
  `hora_fin` VARCHAR(255) NOT NULL,
  `hora_inicio` VARCHAR(255) NOT NULL,
  `grupo_id` BIGINT NULL DEFAULT NULL,
  INDEX `FKp5gxotvxjlr365k51g04k28ar` (`grupo_id` ASC) VISIBLE,
  CONSTRAINT `FKp5gxotvxjlr365k51g04k28ar`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `taemoidb`.`grupo` (`id`)
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`usuario` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `apellidos` VARCHAR(50) NOT NULL,
  `contrasena` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `nombre` VARCHAR(50) NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  UNIQUE INDEX `UK5171l57faosmj8myawaucatdw` (`email` ASC) VISIBLE,
  UNIQUE INDEX `UKiwqbs97sir17hipge3olmu6i6` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FKs2jdpk0wmqgyj2i2jer600y8b`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoidb`.`alumno` (`id`)
);

CREATE TABLE IF NOT EXISTS `taemoidb`.`usuario_rol` (
  `usuario_id` BIGINT NOT NULL,
  `roles` ENUM('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER') NULL DEFAULT NULL,
  INDEX `FKbyfgloj439r9wr9smrms9u33r` (`usuario_id` ASC) VISIBLE,
  CONSTRAINT `FKbyfgloj439r9wr9smrms9u33r`
    FOREIGN KEY (`usuario_id`)
    REFERENCES `taemoidb`.`usuario` (`id`)
);

INSERT INTO `taemoidb`.`imagen` (`datos`, `nombre`, `tipo`) VALUES
('', 'Campeonato Andalucía Junior Pesos Olímpicos', 'image/webp'),
('', 'Campeonato Promoción', 'image/webp'),
('', 'Mascota Taekwondo', 'image/webp'),
('', 'Medallas Promoción Chicas', 'image/webp'),
('', 'Medallas Promoción Chicos', 'image/webp'),
('', 'Día de la mujer 1', 'image/webp'),
('', 'Día de la mujer 2', 'image/webp'),
('', 'Podio Campeonato Nocturno', 'image/webp'),
('', 'Entrenamiento La Algaba', 'image/webp'),
('', 'Competidores Campeonato Nocturno', 'image/webp');

INSERT INTO `taemoidb`.`categoria` (`nombre`, `tipo_categoria`) VALUES
('PreTKD', 'PRETKD'),
('Infantil', 'INFANTIL'),
('Precadete', 'PRECADETE'),
('Cadete', 'CADETE'),
('Junior', 'JUNIOR'),
('Sub 21', 'SUB21'),
('Senior', 'SENIOR');

INSERT INTO `taemoidb`.`grado` (`tipo_grado`) VALUES
('BLANCO'),
('BLANCO_AMARILLO'),
('AMARILLO'),
('AMARILLO_NARANJA'),
('NARANJA'),
('NARANJA_VERDE'),
('VERDE'),
('VERDE_AZUL'),
('AZUL'),
('AZUL_ROJO'),
('ROJO'),
('ROJO_NEGRO'),
('NEGRO');

INSERT INTO `taemoidb`.`grupo` (`nombre`) VALUES
('Grupo Lunes y Miércoles'),
('Grupo Martes y Jueves');

INSERT INTO `taemoidb`.`evento` (`descripcion`, `titulo`, `foto_evento_id`) VALUES
('Prepárate para vivir la emoción del Campeonato Andalucía Junior Peso Olímpico 2024, donde los jóvenes atletas más destacados de la región competirán por la gloria. Este evento de alto nivel reúne a los mejores talentos juveniles en la categoría de peso olímpico, ofreciendo una oportunidad única para presenciar combates llenos de habilidad, técnica y espíritu deportivo.', 'CAMPEONATO ANDALUCÍA JUNIOR PESO OLÍMPICO 2024', 1),
('El Campeonato de Promoción es el evento perfecto para descubrir y celebrar a los futuros campeones. Este emocionante campeonato reúne a los atletas emergentes más talentosos, ofreciéndoles una plataforma para mostrar sus habilidades y competir en un entorno de alto nivel.', 'CAMPEONATO DE PROMOCIÓN', 2),
('¡Únete a nosotros para la presentación de la nueva mascota oficial de Taekwondo! Este evento especial celebra la llegada de un nuevo símbolo de energía, pasión y espíritu deportivo que inspirará a atletas y aficionados por igual. Ven a conocer a nuestra encantadora mascota, diseñada para representar los valores y la emoción del Taekwondo.', 'NUEVA MASCOTA OFICIAL DE TAEKWONDO', 3),
('Celebramos a las increíbles ganadoras del Campeonato de Promoción, donde el talento emergente se encuentra con la grandeza. Estas atletas excepcionales han demostrado su habilidad, determinación y pasión, superando todos los desafíos para alcanzar la cima.', 'GANADORAS DEL CAMPEONATO DE PROMOCIÓN', 4),
('¡Felicidades a los ganadores del Campeonato de Promoción! Estos talentosos atletas han demostrado una increíble dedicación, habilidad y espíritu deportivo, superando todos los desafíos para alzarse con la victoria. Este evento es una celebración de su arduo trabajo y determinación, y una oportunidad para inspirar a la próxima generación de deportistas.', 'GANADORES DEL CAMPEONATO DE PROMOCIÓN', 5),
('¡Únete a nosotros para celebrar la Felicidad y Diversión en el Día de la Mujer! Este evento especial está dedicado a honrar y empoderar a las mujeres de todas las edades. Disfruta de una jornada repleta de actividades emocionantes, talleres inspiradores, y momentos de alegría compartida.', 'FELICIDAD Y DIVERSIÓN EN EL DÍA DE LA MUJER', 6),
('¡Únete a nosotros para celebrar a las Mujeres Valientes y Guerreras! Este evento especial está dedicado a honrar a las mujeres que muestran coraje, fuerza y determinación en cada aspecto de sus vidas. Acompáñanos en un día lleno de inspiradoras historias de superación, actividades empoderadoras y momentos de conexión y solidaridad.', 'MUJERES VALIENTES Y GUERRERAS', 7),
('¡Felicidades a nuestros increíbles competidores en el Campeonato Nocturno! Este emocionante evento ha sido testigo de actuaciones excepcionales, demostrando el talento y la dedicación de nuestros atletas bajo las luces nocturnas.', 'FELICIDADES A NUESTROS COMPETIDORES EN EL CAMPEONATO NOCTURNO', 8),
('¡Echad un vistazo al emocionante entrenamiento de La Algaba! Este evento ofrece una oportunidad única para ver de cerca cómo nuestros dedicados atletas se preparan para la competencia. Observa sesiones de entrenamiento intensivo, técnicas avanzadas y el esfuerzo constante que nuestros deportistas ponen en cada práctica.', 'ECHAD UN VISTAZO AL ENTRENAMIENTO DE LA ALGABA', 9),
('¡Prepárate para la emoción del Campeonato Nocturno! Este evento único trae la competencia al emocionante ambiente de la noche, donde los mejores atletas se enfrentan bajo las estrellas. Vive la intensidad de los combates, la energía vibrante y la destreza impresionante de los competidores en un escenario iluminado.', 'CAMPEONATO NOCTURNO', 10);

INSERT INTO `taemoidb`.`turno` (`dia_semana`, `hora_inicio`, `hora_fin`, `grupo_id`) VALUES
('Lunes', '17:00', '18:00', 1),
('Lunes', '18:00', '19:00', 1),
('Lunes', '19:00', '20:30', 1),
('Martes', '17:00', '18:00', 2),
('Martes', '18:00', '19:00', 2),
('Martes', '19:00', '20:00', 2),
('Miércoles', '17:00', '18:00', 1),
('Miércoles', '18:00', '19:00', 1),
('Miércoles', '19:00', '20:30', 1),
('Jueves', '17:00', '18:00', 2),
('Jueves', '18:00', '19:00', 2),
('Jueves', '19:00', '20:00', 2);
