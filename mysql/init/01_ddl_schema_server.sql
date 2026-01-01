-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
-- -----------------------------------------------------
-- Schema taemoi_db
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema taemoi_db
-- -----------------------------------------------------
DROP SCHEMA IF EXISTS `taemoi_db`;
CREATE SCHEMA IF NOT EXISTS `taemoi_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci ;
USE `taemoi_db` ;

-- -----------------------------------------------------
-- Table `taemoi_db`.`imagen`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`imagen` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nombre` VARCHAR(200) NOT NULL,
  `ruta` VARCHAR(500) NOT NULL,
  `tipo` VARCHAR(50) NOT NULL,
  `url` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`categoria`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`categoria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nombre` VARCHAR(255) NOT NULL,
  `tipo_categoria` ENUM('CADETE', 'INFANTIL', 'JUNIOR', 'PRECADETE', 'SENIOR') NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`grado`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`grado` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tipo_grado` ENUM('AMARILLO', 'AMARILLO_NARANJA', 'AZUL', 'AZUL_ROJO', 'BLANCO', 'BLANCO_AMARILLO', 'NARANJA', 'NARANJA_VERDE', 'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN', 'ROJO', 'ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM', 'VERDE', 'VERDE_AZUL') NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`alumno`
-- -----------------------------------------------------
-- IMPORTANT: Many fields are now nullable for multi-sport mode.
-- In multi-sport mode, per-sport data is stored in alumno_deporte table.
-- Legacy single-sport mode still uses these fields.
CREATE TABLE IF NOT EXISTS `taemoi_db`.`alumno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `activo` BIT(1) NOT NULL,
  `apellidos` VARCHAR(255) NOT NULL,
  `apto_para_examen` BIT(1) NULL DEFAULT NULL,
  `autorizacion_web` BIT(1) NOT NULL,
  `competidor` BIT(1) NULL DEFAULT NULL,
  `cuantia_tarifa` DOUBLE NULL DEFAULT NULL,
  `deporte` ENUM('DEFENSA_PERSONAL_FEMENINA', 'KICKBOXING', 'PILATES', 'TAEKWONDO') NULL DEFAULT NULL,
  `direccion` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NULL DEFAULT NULL,
  `rol_familiar` ENUM('PADRE', 'MADRE', 'HIJO', 'HIJA', 'NINGUNO') NULL DEFAULT NULL,
  `grupo_familiar` VARCHAR(50) NULL DEFAULT NULL,
  `fecha_alta` DATE NULL DEFAULT NULL,
  `fecha_alta_inicial` DATE NULL DEFAULT NULL,
  `fecha_baja` DATE NULL DEFAULT NULL,
  `fecha_grado` DATE NULL DEFAULT NULL,
  `fecha_licencia` DATE NULL DEFAULT NULL,
  `fecha_nacimiento` DATE NOT NULL,
  `fecha_peso` DATE NULL DEFAULT NULL,
  `nif` VARCHAR(9) NOT NULL,
  `nombre` VARCHAR(255) NOT NULL,
  `numero_expediente` INT NOT NULL,
  `numero_licencia` INT NULL DEFAULT NULL,
  `peso` DOUBLE NULL DEFAULT NULL,
  `telefono` INT NOT NULL,
  `tiene_derecho_examen` BIT(1) NOT NULL,
  `tiene_discapacidad` BIT(1) NULL DEFAULT NULL,
  `tiene_licencia` BIT(1) NULL DEFAULT NULL,
  `tipo_tarifa` ENUM('ADULTO', 'ADULTO_GRUPO', 'DEFENSA_PERSONAL_FEMENINA', 'FAMILIAR', 'HERMANOS', 'INFANTIL', 'INFANTIL_GRUPO', 'KICKBOXING', 'PADRES_HIJOS', 'PILATES') NULL DEFAULT NULL,
  `categoria_id` BIGINT NULL DEFAULT NULL,
  `foto_alumno_id` BIGINT NULL DEFAULT NULL,
  `grado_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UKi2otd4eccxkj7jwqeygr3wgoj` (`numero_expediente` ASC) VISIBLE,
  UNIQUE INDEX `UKjuke9njf11mdvhyauigofa3g2` (`foto_alumno_id` ASC) VISIBLE,
  INDEX `FK3sss574xtkjpgdljygxm19glf` (`categoria_id` ASC) VISIBLE,
  INDEX `FKid01ntlqpypy38pi2bk0nlof3` (`grado_id` ASC) VISIBLE,
  CONSTRAINT `FK35h62thngrq0o15s2www7u451`
    FOREIGN KEY (`foto_alumno_id`)
    REFERENCES `taemoi_db`.`imagen` (`id`),
  CONSTRAINT `FK3sss574xtkjpgdljygxm19glf`
    FOREIGN KEY (`categoria_id`)
    REFERENCES `taemoi_db`.`categoria` (`id`),
  CONSTRAINT `FKid01ntlqpypy38pi2bk0nlof3`
    FOREIGN KEY (`grado_id`)
    REFERENCES `taemoi_db`.`grado` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`producto`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`producto` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `concepto` VARCHAR(255) NULL DEFAULT NULL,
  `precio` DOUBLE NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`alumno_deporte`
-- -----------------------------------------------------
-- Per-sport data for multi-sport mode
-- Each student can have multiple sports with independent configuration
CREATE TABLE IF NOT EXISTS `taemoi_db`.`alumno_deporte` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `activo` BIT(1) NOT NULL,
  `apto_para_examen` BIT(1) NOT NULL,
  `deporte` ENUM('DEFENSA_PERSONAL_FEMENINA', 'KICKBOXING', 'PILATES', 'TAEKWONDO') NOT NULL,
  `fecha_alta` DATE NULL DEFAULT NULL,
  `fecha_alta_inicial` DATE NULL DEFAULT NULL,
  `fecha_baja` DATE NULL DEFAULT NULL,
  `fecha_grado` DATE NULL DEFAULT NULL,
  `fecha_licencia` DATE NULL DEFAULT NULL,
  `fecha_peso` DATE NULL DEFAULT NULL,
  `tipo_tarifa` ENUM('ADULTO', 'ADULTO_GRUPO', 'DEFENSA_PERSONAL_FEMENINA', 'FAMILIAR', 'HERMANOS', 'INFANTIL', 'INFANTIL_GRUPO', 'KICKBOXING', 'PADRES_HIJOS', 'PILATES') NULL DEFAULT NULL,
  `cuantia_tarifa` DOUBLE NULL DEFAULT NULL,
  `rol_familiar` ENUM('PADRE', 'MADRE', 'HIJO', 'HIJA', 'NINGUNO') NULL DEFAULT NULL,
  `grupo_familiar` VARCHAR(50) NULL DEFAULT NULL,
  `competidor` BIT(1) NOT NULL DEFAULT 0,
  `fecha_alta_competicion` DATE NULL DEFAULT NULL,
  `fecha_alta_competidor_inicial` DATE NULL DEFAULT NULL,
  `categoria_id` BIGINT NULL DEFAULT NULL,
  `peso` DOUBLE NULL DEFAULT NULL,
  `tiene_licencia` BIT(1) NOT NULL DEFAULT 0,
  `numero_licencia` INT NULL DEFAULT NULL,
  `alumno_id` BIGINT NOT NULL,
  `grado_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UKloubbjnmncoc7hi1oasugm5fa` (`alumno_id` ASC, `deporte` ASC) VISIBLE,
  INDEX `idx_alumno_deporte_alumno_id` (`alumno_id` ASC) VISIBLE,
  INDEX `idx_alumno_deporte_deporte` (`deporte` ASC) VISIBLE,
  INDEX `idx_alumno_deporte_apto` (`deporte` ASC, `apto_para_examen` ASC) VISIBLE,
  INDEX `FKirt39noykqeplscbxi32tb786` (`grado_id` ASC) VISIBLE,
  INDEX `FKcategoria_alumno_deporte_init` (`categoria_id` ASC) VISIBLE,
  CONSTRAINT `FK9fr1k4ow5l16qts5508vqk9wn`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`),
  CONSTRAINT `FKirt39noykqeplscbxi32tb786`
    FOREIGN KEY (`grado_id`)
    REFERENCES `taemoi_db`.`grado` (`id`),
  CONSTRAINT `FKcategoria_alumno_deporte_init`
    FOREIGN KEY (`categoria_id`)
    REFERENCES `taemoi_db`.`categoria` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`producto_alumno`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`producto_alumno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `cantidad` INT NULL DEFAULT NULL,
  `concepto` VARCHAR(255) NULL DEFAULT NULL,
  `fecha_asignacion` DATETIME(6) NULL DEFAULT NULL,
  `fecha_pago` DATETIME(6) NULL DEFAULT NULL,
  `notas` VARCHAR(255) NULL DEFAULT NULL,
  `pagado` BIT(1) NULL DEFAULT NULL,
  `precio` DOUBLE NULL DEFAULT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  `producto_id` BIGINT NULL DEFAULT NULL,
  `alumno_deporte_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FK5aq2e0hk0dllyuil2f7rn526r` (`alumno_id` ASC) VISIBLE,
  INDEX `FKhr3yv7e998cyd9np3nod2uo0j` (`producto_id` ASC) VISIBLE,
  INDEX `FKmi1jp8li7aa59orox8hohl213` (`alumno_deporte_id` ASC) VISIBLE,
  CONSTRAINT `FK5aq2e0hk0dllyuil2f7rn526r`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`),
  CONSTRAINT `FKhr3yv7e998cyd9np3nod2uo0j`
    FOREIGN KEY (`producto_id`)
    REFERENCES `taemoi_db`.`producto` (`id`),
  CONSTRAINT `FKmi1jp8li7aa59orox8hohl213`
    FOREIGN KEY (`alumno_deporte_id`)
    REFERENCES `taemoi_db`.`alumno_deporte` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`convocatoria`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`convocatoria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `deporte` ENUM('DEFENSA_PERSONAL_FEMENINA', 'KICKBOXING', 'PILATES', 'TAEKWONDO') NOT NULL,
  `fecha_convocatoria` DATE NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`alumno_convocatoria`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`alumno_convocatoria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `cuantia_examen` DOUBLE NOT NULL,
  `fecha_pago` DATE NULL DEFAULT NULL,
  `grado_actual` ENUM('AMARILLO', 'AMARILLO_NARANJA', 'AZUL', 'AZUL_ROJO', 'BLANCO', 'BLANCO_AMARILLO', 'NARANJA', 'NARANJA_VERDE', 'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN', 'ROJO', 'ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM', 'VERDE', 'VERDE_AZUL') NULL DEFAULT NULL,
  `grado_siguiente` ENUM('AMARILLO', 'AMARILLO_NARANJA', 'AZUL', 'AZUL_ROJO', 'BLANCO', 'BLANCO_AMARILLO', 'NARANJA', 'NARANJA_VERDE', 'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN', 'ROJO', 'ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM', 'VERDE', 'VERDE_AZUL') NULL DEFAULT NULL,
  `pagado` BIT(1) NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  `convocatoria_id` BIGINT NULL DEFAULT NULL,
  `producto_alumno_id` BIGINT NULL DEFAULT NULL,
  `alumno_deporte_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FKpm62kpl6nbmqiuach08e2t3sk` (`alumno_id` ASC) VISIBLE,
  INDEX `FKqwjl8svxuu5mywp3cnosoewrk` (`convocatoria_id` ASC) VISIBLE,
  INDEX `FK7i5raysl38e3apc5kow9mtii2` (`producto_alumno_id` ASC) VISIBLE,
  INDEX `FKgor9jlkap0uaen39m2eon5ct8` (`alumno_deporte_id` ASC) VISIBLE,
  CONSTRAINT `FK7i5raysl38e3apc5kow9mtii2`
    FOREIGN KEY (`producto_alumno_id`)
    REFERENCES `taemoi_db`.`producto_alumno` (`id`),
  CONSTRAINT `FKgor9jlkap0uaen39m2eon5ct8`
    FOREIGN KEY (`alumno_deporte_id`)
    REFERENCES `taemoi_db`.`alumno_deporte` (`id`),
  CONSTRAINT `FKpm62kpl6nbmqiuach08e2t3sk`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`),
  CONSTRAINT `FKqwjl8svxuu5mywp3cnosoewrk`
    FOREIGN KEY (`convocatoria_id`)
    REFERENCES `taemoi_db`.`convocatoria` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`grupo`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`grupo` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nombre` VARCHAR(50) NOT NULL,
  `tipo` VARCHAR(255) NULL DEFAULT NULL,
  `deporte` ENUM('DEFENSA_PERSONAL_FEMENINA', 'KICKBOXING', 'PILATES', 'TAEKWONDO') NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`alumno_grupo`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`alumno_grupo` (
  `grupo_id` BIGINT NOT NULL,
  `alumno_id` BIGINT NOT NULL,
  INDEX `FK9m58u5wqf6fc2i5qur535qic6` (`alumno_id` ASC) VISIBLE,
  INDEX `FKc3q87xf5n31xgpfun4b7jndg0` (`grupo_id` ASC) VISIBLE,
  CONSTRAINT `FK9m58u5wqf6fc2i5qur535qic6`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`),
  CONSTRAINT `FKc3q87xf5n31xgpfun4b7jndg0`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `taemoi_db`.`grupo` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`turno`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`turno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dia_semana` VARCHAR(255) NOT NULL,
  `hora_fin` VARCHAR(255) NOT NULL,
  `hora_inicio` VARCHAR(255) NOT NULL,
  `tipo` VARCHAR(255) NULL DEFAULT NULL,
  `grupo_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FKp5gxotvxjlr365k51g04k28ar` (`grupo_id` ASC) VISIBLE,
  CONSTRAINT `FKp5gxotvxjlr365k51g04k28ar`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `taemoi_db`.`grupo` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`alumno_turno`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`alumno_turno` (
  `alumno_id` BIGINT NOT NULL,
  `turno_id` BIGINT NOT NULL,
  INDEX `FKfoc1h7mfnqjam1hnegu45moyt` (`turno_id` ASC) VISIBLE,
  INDEX `FKl8eyv28lwnv3gqgthkf1eatuf` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FKfoc1h7mfnqjam1hnegu45moyt`
    FOREIGN KEY (`turno_id`)
    REFERENCES `taemoi_db`.`turno` (`id`),
  CONSTRAINT `FKl8eyv28lwnv3gqgthkf1eatuf`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`configuracion_sistema`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`configuracion_sistema` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `clave` VARCHAR(255) NOT NULL,
  `valor` INT NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`documento`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`documento` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nombre` VARCHAR(200) NOT NULL,
  `ruta` VARCHAR(500) NOT NULL,
  `tipo` VARCHAR(50) NOT NULL,
  `url` VARCHAR(255) NULL DEFAULT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FK33wv3r0e5tl1xy0hw27g55p2l` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FK33wv3r0e5tl1xy0hw27g55p2l`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`evento`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`evento` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `descripcion` VARCHAR(500) NOT NULL,
  `titulo` VARCHAR(100) NOT NULL,
  `foto_evento_id` BIGINT NULL DEFAULT NULL,
  `visible` BIT(1) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UK4181ienduxeq2ridhuwd3qvwk` (`foto_evento_id` ASC) VISIBLE,
  CONSTRAINT `FK8r4xhbhw72x5gsalwsmf6km67`
    FOREIGN KEY (`foto_evento_id`)
    REFERENCES `taemoi_db`.`imagen` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`usuario`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`usuario` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `apellidos` VARCHAR(50) NOT NULL,
  `contrasena` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `nombre` VARCHAR(50) NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  `auth_provider` VARCHAR(20) NULL DEFAULT NULL,
  `reset_token_hash` VARCHAR(64) NULL DEFAULT NULL,
  `reset_token_expires_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UK5171l57faosmj8myawaucatdw` (`email` ASC) VISIBLE,
  UNIQUE INDEX `UKiwqbs97sir17hipge3olmu6i6` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FKs2jdpk0wmqgyj2i2jer600y8b`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`registro_pendiente`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`registro_pendiente` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `alumno_id` BIGINT NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `token_hash` VARCHAR(64) NOT NULL,
  `token_expires_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UK_registro_pendiente_email` (`email` ASC) VISIBLE,
  INDEX `IDX_registro_pendiente_token` (`token_hash` ASC) VISIBLE,
  INDEX `FK_registro_pendiente_alumno` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FK_registro_pendiente_alumno`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoi_db`.`alumno` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoi_db`.`usuario_rol`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoi_db`.`usuario_rol` (
  `usuario_id` BIGINT NOT NULL,
  `roles` ENUM('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER') NULL DEFAULT NULL,
  INDEX `FKbyfgloj439r9wr9smrms9u33r` (`usuario_id` ASC) VISIBLE,
  CONSTRAINT `FKbyfgloj439r9wr9smrms9u33r`
    FOREIGN KEY (`usuario_id`)
    REFERENCES `taemoi_db`.`usuario` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
