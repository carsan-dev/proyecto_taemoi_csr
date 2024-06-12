-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
-- -----------------------------------------------------
-- Schema taemoidb
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema taemoidb
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `taemoidb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci ;
USE `taemoidb` ;

-- -----------------------------------------------------
-- Table `taemoidb`.`imagen`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`imagen` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `datos` LONGBLOB NOT NULL,
  `nombre` VARCHAR(200) NOT NULL,
  `tipo` VARCHAR(50) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`categoria`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`categoria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nombre` VARCHAR(255) NOT NULL,
  `tipo_categoria` ENUM('CADETE', 'INFANTIL', 'JUNIOR', 'PRECADETE', 'PRETKD', 'SENIOR', 'SUB21') NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 8
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`grado`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`grado` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tipo_grado` ENUM('AMARILLO', 'AMARILLO_NARANJA', 'AZUL', 'AZUL_ROJO', 'BLANCO', 'BLANCO_AMARILLO', 'NARANJA', 'NARANJA_VERDE', 'NEGRO', 'ROJO', 'ROJO_NEGRO', 'VERDE', 'VERDE_AZUL') NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 14
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`alumno`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`alumno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
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
  PRIMARY KEY (`id`),
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
    REFERENCES `taemoidb`.`grado` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 21
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`grupo`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`grupo` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nombre` VARCHAR(50) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 3
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`alumno_grupo`
-- -----------------------------------------------------
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
    REFERENCES `taemoidb`.`grupo` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`evento`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`evento` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `descripcion` VARCHAR(500) NOT NULL,
  `titulo` VARCHAR(100) NOT NULL,
  `foto_evento_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UK4181ienduxeq2ridhuwd3qvwk` (`foto_evento_id` ASC) VISIBLE,
  CONSTRAINT `FK8r4xhbhw72x5gsalwsmf6km67`
    FOREIGN KEY (`foto_evento_id`)
    REFERENCES `taemoidb`.`imagen` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`examen`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`examen` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `fecha` DATE NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  `examen_grado_id` BIGINT NULL DEFAULT NULL,
  `grado_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
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
    REFERENCES `taemoidb`.`grado` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`pago`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`pago` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `concepto` VARCHAR(255) NOT NULL,
  `cuantia` DOUBLE NOT NULL,
  `estado` ENUM('NO_PAGADO', 'PAGADO') NOT NULL,
  `fecha` DATE NOT NULL,
  `numero_expediente_alumno` VARCHAR(255) NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FKaxf1p403r03amcvwbacx9rusq` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FKaxf1p403r03amcvwbacx9rusq`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoidb`.`alumno` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`turno`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`turno` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dia_semana` VARCHAR(255) NOT NULL,
  `hora_fin` VARCHAR(255) NOT NULL,
  `hora_inicio` VARCHAR(255) NOT NULL,
  `grupo_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FKp5gxotvxjlr365k51g04k28ar` (`grupo_id` ASC) VISIBLE,
  CONSTRAINT `FKp5gxotvxjlr365k51g04k28ar`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `taemoidb`.`grupo` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 13
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`usuario`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`usuario` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `apellidos` VARCHAR(50) NOT NULL,
  `contrasena` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `nombre` VARCHAR(50) NOT NULL,
  `alumno_id` BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `UK5171l57faosmj8myawaucatdw` (`email` ASC) VISIBLE,
  UNIQUE INDEX `UKiwqbs97sir17hipge3olmu6i6` (`alumno_id` ASC) VISIBLE,
  CONSTRAINT `FKs2jdpk0wmqgyj2i2jer600y8b`
    FOREIGN KEY (`alumno_id`)
    REFERENCES `taemoidb`.`alumno` (`id`))
ENGINE = InnoDB
AUTO_INCREMENT = 24
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `taemoidb`.`usuario_rol`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `taemoidb`.`usuario_rol` (
  `usuario_id` BIGINT NOT NULL,
  `roles` ENUM('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER') NULL DEFAULT NULL,
  INDEX `FKbyfgloj439r9wr9smrms9u33r` (`usuario_id` ASC) VISIBLE,
  CONSTRAINT `FKbyfgloj439r9wr9smrms9u33r`
    FOREIGN KEY (`usuario_id`)
    REFERENCES `taemoidb`.`usuario` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO `taemoidb`.`categoria` (`nombre`, `tipo_categoria`) VALUES
('PRETKD', 'PRETKD'),
('INFANTIL', 'INFANTIL'),
('PRECADETE', 'PRECADETE'),
('CADETE', 'CADETE'),
('JUNIOR', 'JUNIOR'),
('SUB21', 'SUB21'),
('SENIOR', 'SENIOR');

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

INSERT INTO `taemoidb`.`alumno` (`nombre`, `apellidos`, `numero_expediente`, `fecha_nacimiento`, `nif`, `direccion`, `telefono`, `email`, `tipo_tarifa`, `cuantia_tarifa`, `fecha_alta`, `fecha_baja`, `categoria_id`, `grado_id`, `foto_alumno_id`) VALUES
('Juan', 'Perez', 1, '2010-05-01', '12345678A', 'Calle Falsa 123', 123456789, 'perez@example.com', 'INFANTIL', 25.0, '2023-01-01', NULL, 4, 4, NULL),
('Luis', 'Garcia', 2, '2008-07-01', '87654321B', 'Avenida Siempre Viva 742', 987654321, 'garcia@example.com', 'ADULTO', 30.0, '2023-02-01', NULL, 5, 5, NULL),
('Ana', 'Martinez', 3, '2006-12-01', '11223344C', 'Calle Luna 456', 112233445, 'martinez@example.com', 'ADULTO_GRUPO', 20.0, '2023-03-01', NULL, 5, 7, NULL),
('Maria', 'Lopez', 4, '2012-03-01', '22334455D', 'Calle Sol 789', 223344556, 'lopez@example.com', 'FAMILIAR', 0.0, '2023-04-01', NULL, 4, 2, NULL),
('Carlos', 'Fernandez', 5, '2005-09-01', '33445566E', 'Calle Estrella 101', 334455667, 'fernandez@example.com', 'ADULTO_GRUPO', 20.0, '2023-05-01', NULL, 6, 9, NULL),
('Lucia', 'Gomez', 6, '2004-01-01', '44556677F', 'Calle Universo 102', 445566778, 'gomez@example.com', 'FAMILIAR', 0.0, '2023-06-01', NULL, 6, 7, NULL),
('Miguel', 'Ruiz', 7, '2015-11-01', '55667788G', 'Calle Planeta 103', 556677889, 'ruiz@example.com', 'HERMANOS', 23.0, '2023-07-01', NULL, 2, 6, NULL),
('Laura', 'Sanchez', 8, '2013-08-01', '66778899H', 'Calle Galaxia 104', 667788990, 'sanchez@example.com', 'INFANTIL_GRUPO', 20.0, '2023-08-01', NULL, 3, 4, NULL),
('David', 'Diaz', 9, '2007-02-01', '77889900I', 'Calle Cosmos 105', 778899001, 'diaz@example.com', 'PADRES_HIJOS', 0.0, '2023-09-01', NULL, 5, 11, NULL),
('Elena', 'Torres', 10, '1999-06-01', '88990011J', 'Calle Estacion 106', 889900112, 'torres@example.com', 'INFANTIL', 25.0, '2023-10-01', NULL, 7, 13, NULL);

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
