package com.taemoi.project.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.RegistroPendiente;

@Repository
public interface RegistroPendienteRepository extends JpaRepository<RegistroPendiente, Long> {
	Optional<RegistroPendiente> findByEmail(String email);

	Optional<RegistroPendiente> findByTokenHash(String tokenHash);
}
