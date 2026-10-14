package com.taemoi.project.entities;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Usuario implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "El nombre no puede estar en blanco")
	@Size(max = 50, message = "El nombre no puede tener más de 50 caracteres")
	private String nombre;

	@NotBlank(message = "Los apellidos no pueden estar en blanco")
	@Size(max = 50, message = "Los apellidos no pueden tener más de 50 caracteres")
	private String apellidos;

	@Column(unique = true)
	@Email(message = "La dirección de correo electrónico debe ser válida")
	@NotBlank(message = "El correo electrónico no puede estar en blanco")
	private String email;

	@NotBlank(message = "La contraseña no puede estar en blanco")
	@Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
private String contrasena;

	@Enumerated(EnumType.STRING)
	@Column(name = "auth_provider")
	private AuthProvider authProvider = AuthProvider.LOCAL;

	@Column(name = "reset_token_hash", length = 64)
	private String resetTokenHash;

	@Column(name = "reset_token_expires_at")
	private LocalDateTime resetTokenExpiresAt;

	@Column(name = "recordatorio_racha_email_habilitado", nullable = false)
	private Boolean recordatorioRachaEmailHabilitado = false;

	@Column(name = "ultimo_recordatorio_racha_reset")
	private LocalDate ultimoRecordatorioRachaReset;

	/**
	 * Roles globales heredados ({@code usuario_rol}). OBSOLETO desde V20: los permisos viven en
	 * {@link Membresia} por organización. Ya no se escriben ni se usan para autorizar; se
	 * mantienen mapeados hasta la contracción para que borrar un usuario borre también sus filas.
	 */
	@Deprecated
	@ElementCollection(fetch = FetchType.EAGER, targetClass = Roles.class)
	@Enumerated(EnumType.STRING)
	@CollectionTable(name = "usuario_rol")
	@Column(name = "Roles")
	private Set<Roles> roles = new HashSet<>();

	/** Escuela elegida en el último inicio de sesión o cambio de escuela (V20). */
	@Column(name = "ultima_organizacion_id")
	private Long ultimaOrganizacionId;

	/**
	 * LAZY: el alumno es de una organización ({@code @TenantId}) y el usuario es global. Cargar un
	 * usuario (p. ej. al autenticar, antes de conocer la escuela) no debe cargar un alumno que puede
	 * ser de otra escuela. Usar {@link #getAlumnoId()} para obtener el id sin inicializarlo.
	 */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alumno_id")
	@JsonBackReference
	private Alumno alumno;

	/**
	 * Siempre vacío: las autoridades dependen de la organización activa y las calcula
	 * {@code MembresiaService} a partir de las membresías (ver {@code JwtAuthenticationFilter}).
	 * No usar como fuente de permisos.
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.emptySet();
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String getPassword() {
		return contrasena;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}

	/** @deprecated ver el campo {@code roles}: usar {@code MembresiaService}. */
	@Deprecated
	public Set<Roles> getRoles() {
		return roles;
	}

	/** @deprecated ver el campo {@code roles}: usar {@code MembresiaService}. */
	@Deprecated
	public void setRoles(Set<Roles> roles) {
		this.roles = roles;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	/** Id del alumno asociado sin inicializar el proxy LAZY (no consulta la base de datos). */
	@JsonIgnore
	public Long getAlumnoId() {
		if (alumno == null) {
			return null;
		}
		if (alumno instanceof HibernateProxy proxy) {
			return (Long) proxy.getHibernateLazyInitializer().getIdentifier();
		}
		return alumno.getId();
	}

	@JsonIgnore
	public Long getUltimaOrganizacionId() {
		return ultimaOrganizacionId;
	}

	public void setUltimaOrganizacionId(Long ultimaOrganizacionId) {
		this.ultimaOrganizacionId = ultimaOrganizacionId;
	}

	public void setAlumno(Alumno alumno) {
		this.alumno = alumno;
	}

	public AuthProvider getAuthProvider() {
		return authProvider == null ? AuthProvider.LOCAL : authProvider;
	}

	public void setAuthProvider(AuthProvider authProvider) {
		this.authProvider = authProvider;
	}

	public String getResetTokenHash() {
		return resetTokenHash;
	}

	public void setResetTokenHash(String resetTokenHash) {
		this.resetTokenHash = resetTokenHash;
	}

	public LocalDateTime getResetTokenExpiresAt() {
		return resetTokenExpiresAt;
	}

	public void setResetTokenExpiresAt(LocalDateTime resetTokenExpiresAt) {
		this.resetTokenExpiresAt = resetTokenExpiresAt;
	}

	public Boolean getRecordatorioRachaEmailHabilitado() {
		return recordatorioRachaEmailHabilitado;
	}

	public void setRecordatorioRachaEmailHabilitado(Boolean recordatorioRachaEmailHabilitado) {
		this.recordatorioRachaEmailHabilitado = Boolean.TRUE.equals(recordatorioRachaEmailHabilitado);
	}

	public LocalDate getUltimoRecordatorioRachaReset() {
		return ultimoRecordatorioRachaReset;
	}

	public void setUltimoRecordatorioRachaReset(LocalDate ultimoRecordatorioRachaReset) {
		this.ultimoRecordatorioRachaReset = ultimoRecordatorioRachaReset;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}
}
