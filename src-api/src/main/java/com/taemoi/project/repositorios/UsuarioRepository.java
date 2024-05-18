package com.taemoi.project.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Usuario;

/**
 * Repositorio para la entidad Usuario. Proporciona métodos para buscar tanto por id como por 
 * email y para validar si existe el email buscado
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
	
    /**
     * Recupera un usuario de la base de datos por su dirección de correo electrónico.
     * 
     * @param email El correo electrónico del usuario a buscar.
     * @return Un Optional que puede contener el usuario correspondiente al correo electrónico especificado.
     */
	Optional<Usuario> findByEmail(String email);

    /**
     * Recupera un usuario de la base de datos por su identificador único.
     * 
     * @param id El identificador único del usuario a buscar.
     * @return Un Optional que puede contener el usuario correspondiente al ID especificado.
     * @throws NullPointerException si el argumento id es nulo.
     */
	@NonNull
	Optional<Usuario> findById(@NonNull Long id);

    /**
     * Verifica si existe un usuario en la base de datos con la dirección de correo electrónico especificada.
     * 
     * @param email El correo electrónico a verificar.
     * @return true si existe un usuario con el correo electrónico especificado, false de lo contrario.
     */
	Boolean existsByEmail(String email);
}
